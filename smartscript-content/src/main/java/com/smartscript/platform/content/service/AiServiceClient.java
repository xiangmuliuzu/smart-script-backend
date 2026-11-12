package com.smartscript.platform.content.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;

/**
 * AI 大模型客户端（App 端 AI 大纲 / 写作 / 润色）。
 *
 * 直接调用 OpenAI 兼容的 Chat Completions 接口（默认 DeepSeek：https://api.deepseek.com），
 * 由 Java 后端持有密钥并转发，App 端不接触密钥。
 *
 * 配置（均来自环境变量 / 本机私有 .env.local，禁止硬编码或提交密钥）：
 *   AI_BASE_URL      大模型基址（默认 https://api.deepseek.com）
 *   AI_API_KEY       接口密钥（必填，缺失则创作接口返回 503）
 *   AI_SERVICE_MODEL 模型名（默认 deepseek-flash）
 *
 * 失败处理：网络异常、HTTP 非 2xx、content 为空，一律抛
 * {@link AiServiceUnavailableException}，由控制器转 503，**绝不返回编造内容**。
 */
@Component
public class AiServiceClient
{
    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 风格里带「共 N 集」时按分集出大纲（一键生成整部走这里）。 */
    private static final Pattern EPISODE_COUNT = Pattern.compile("共\\s*(\\d{1,3})\\s*集");

    /** 流式调用专用 HTTP 客户端（JDK 内置，支持 SSE 逐块读取）。 */
    private final HttpClient streamClient;

    /** 单次生成 token 上限配置（sys_config 可调；缺失时回退内置默认）。 */
    @Autowired(required = false)
    private AiGenLimitConfig genLimitConfig;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiServiceClient.class);

    public AiServiceClient(
            @Value("${AI_BASE_URL:https://api.deepseek.com}") String baseUrl,
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_SERVICE_MODEL:deepseek-flash}") String model)
    {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
        this.streamClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
    }

    /**
     * AI 写作：直接产出小说正文。
     *
     * @param skillContent 导演(skill) 指令（可选；命中时追加到 system prompt）
     * @return 结果对象：content=正文全文，totalTokens=本次消耗 token（usage.total_tokens），model=实际模型
     */
    public AiCallResult write(String prompt, String type, String skillContent)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深网络小说写手，擅长创作节奏紧凑、画面感强、对话自然的小说正文。"
                + "请根据用户的要求直接创作小说正文，不要解释、不要标题、不要任何前后缀。";
        return chat(appendSkill(system, skillContent), prompt + typeHint);
    }

    /**
     * AI 润色：保持原意，按指定风格优化。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=润色全文，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult polish(String content, String style, String skillContent)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        return chat(appendSkill(system, skillContent), content);
    }

    /**
     * AI 剧本（原"大纲"）：面向完全不懂创作的新手，生成结构完整的剧本大纲。
     *
     * 定位：让没有创作经验的人也能得到一份「可直接展开成剧本」的完整大纲，
     * 后续可继续走 AI 分镜做成可直接出售的分镜剧本。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=模型返回的原始大纲文本（JSON 或兜底文本），
     *                   totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult outline(String inspiration, String genre, String style, String skillContent)
    {
        String genreHint = (genre == null || genre.isBlank()) ? "不限" : genre;
        String styleHint = (style == null || style.isBlank()) ? "不限" : style;
        String insp = (inspiration == null || inspiration.isBlank()) ? "" : inspiration;
        String system = "你是一名资深网文策划编辑，擅长帮完全不懂创作的普通人快速产出专业级剧本大纲。"
                + "请根据用户给出的灵感、题材与风格，生成一份结构完整、可直接展开成剧本的小说大纲；"
                + "要求用通俗易懂的语言，每个分幕写清推进逻辑与关键冲突，让新手照着写也能写成不错的故事。"
                + "必须只输出一个合法 JSON 对象，不要输出任何解释，也不要使用 Markdown 代码块。"
                + "JSON 结构如下："
                + "{\"title\":\"作品名\",\"logline\":\"一句话故事\","
                + "\"characters\":[{\"name\":\"角色名\",\"role\":\"身份或定位\"}],"
                + "\"acts\":[{\"name\":\"第一幕\",\"summary\":\"本幕概述\",\"beats\":[\"关键情节1\",\"关键情节2\"]}]}";
        String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint;
        return chat(appendSkill(system, skillContent), user);
    }

    /**
     * AI 分镜：把剧本文本拆解为可直接用于拍摄/出售的分镜脚本。
     *
     * 输出为中文可读文本，按场景分镜：每个镜头给出 镜号/景别/运镜/时长/画面内容/台词/音效配乐/转场。
     * 分镜输出规范与「AI Skill 配置」内置 seed skill（悬疑/甜宠导演）中的分镜章节保持一致，
     * 供后续接入视频制作或直接作为成品分镜剧本出售。
     *
     * @param script       剧本文本（必填；可以是 AI 剧本产出）
     * @param style        分镜风格（可选：电影感/短剧竖屏/动漫风等）
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=分镜脚本全文，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult storyboard(String script, String style, String skillContent)
    {
        String styleHint = (style == null || style.isBlank()) ? "电影感" : style;
        String system = "你是一名资深影视分镜师，擅长把剧本转化为可直接用于拍摄或出售的专业分镜脚本。"
                + "请根据用户提供的剧本文本，按场景拆解为完整分镜；每场戏开头写清场次与场景地点，"
                + "每个镜头依次给出：镜号、景别、运镜、时长、画面内容、台词、音效/配乐、转场。"
                + "画面内容要具体到人物动作、表情与构图；台词直接引用或精简改写剧本对白；"
                + "整体风格：" + styleHint + "。直接用中文输出分镜正文，不要解释、不要前后缀。";
        String user = "剧本文本：\n" + script;
        return chat(appendSkill(system, skillContent), user);
    }

    // ================================================================
    // 流式创作（SSE）：逐块回调内容增量，供 App/PC 端打字机式展示。
    // 返回完整内容与本次 token 消耗（调用方用于落库/按 token 扣积分）；中途失败抛异常，绝不编造。
    // ================================================================

    /**
     * AI 写作（流式）。[onDelta] 每收到一个内容增量回调一次（含空格/换行）。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整正文，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult writeStream(String prompt, String type, String skillContent, Consumer<String> onDelta)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深短剧编剧，擅长把分集事件写成一集可直接排演的剧本正文。"
                + "写法要求：开头一句自然交代本集发生的地点和时间（如“顾家老宅的院子里，午后的日头正毒”），"
                + "不要写“场景1”“内景/外景”这类场景头，也不要写镜号、景别、运镜、转场，这些交给分镜环节；"
                + "正文以人物动作和对白推进，对白用“角色：台词”格式，句子短、有来有回；"
                + "篇幅 700-900 字，只写这一集，本集要有完整结果，结尾留一个具体钩子（一个动作、一句关键台词或一个未解的画面）；"
                + "不要解释、不要章节标题、不要任何前后缀。";
        return streamChat(appendSkill(system, skillContent), prompt + typeHint, onDelta);
    }

    /**
     * AI 润色（流式）。[onDelta] 每收到一个内容增量回调一次。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整润色结果，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult polishStream(String content, String style, String skillContent, Consumer<String> onDelta)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        return streamChat(appendSkill(system, skillContent), content, onDelta);
    }

    /**
     * AI 剧本（原"大纲"，流式）。[onDelta] 每收到一个内容增量回调一次（可读剧本大纲文本）。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整可读剧本大纲文本，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult outlineStream(String inspiration, String genre, String style, String skillContent, Consumer<String> onDelta)
    {
        return outlineStreamBatched(inspiration, genre, style, skillContent, null, onDelta, null, null, null);
    }

    /**
     * 剧本大纲（流式，带创作台账）。台账内容会注入到设定与每批事件卡，保证跨批人物关系一致。
     *
     * @param ledgerContext 创作台账文本（人物卡/关系/伏笔/时间线/物品/线索，可空）
     */
    public AiCallResult outlineStream(String inspiration, String genre, String style, String skillContent,
            String ledgerContext, Consumer<String> onDelta)
    {
        return outlineStreamBatched(inspiration, genre, style, skillContent, ledgerContext, onDelta, null, null, null);
    }

    /**
     * 剧本大纲分批生成（两阶段，各自独立额度）：
     *   阶段1：三块设定（故事简介 / 主要人物 / 人物关系）单独一次调用，额度独立（4096），
     *          不再与每集事件卡挤在同一次输出里；
     *   阶段2：分集事件卡按 6 集一批，每批独立调用（8192），
     *          每批注入设定精简版 + 创作台账（人物/关系/伏笔/时间线/物品/线索），
     *          保证跨批人物、关系、伏笔不跑偏。
     */
    private AiCallResult outlineStreamBatched(String inspiration, String genre, String style,
                                              String skillContent, String ledgerContext,
                                              Consumer<String> onDelta,
                                              String baseUrl, String apiKey, String model)
    {
        /** 设定块单次额度（与事件卡、正文额度互不占用，可在 sys_config 单独调整） */
        final int settingMaxTokens = tokenLimit("setting", 8192);
        /** 事件卡单批额度 */
        final int episodeMaxTokens = tokenLimit("episode", 8192);
        /** 首批事件卡注入设定原文的上限 */
        final int settingClipFirst = 3000;
        /** 后续批注入设定摘要的上限 */
        final int settingClipCont = 1200;
        /** 台账注入上限 */
        final int ledgerClip = 2000;

        String genreHint = (genre == null || genre.isBlank()) ? "不限" : genre;
        String styleHint = (style == null || style.isBlank()) ? "不限" : style;
        String insp = (inspiration == null || inspiration.isBlank()) ? "" : inspiration;
        String base = "你是一名资深短剧编剧兼分集策划，擅长把灵感拆成一集一集、每集都能独立成片的完整小事件。"
                + "输出格式严格如下："
                + "第N集：事件名\n本集事件：……\n冲突与推进：……\n结果或反转：……\n结尾钩子：……\n本集出场：角色A、角色B"
                + "事件名要短（4-8字）且点明本集核心事件；每项 30-60 字，写具体的人做什么、说什么，"
                + "不要写场景头、镜号、景别、运镜（这些交给后续分镜环节）。"
                + "每集必须是一件完整的事：有开场、有交锋、有结果；结尾钩子要具体（一个动作、一句关键台词或一个未解的画面）；"
                + "「本集出场」只写本集真正出镜的 2-4 个人物姓名（用设定里的原名），没有出场的人物不要写，"
                + "写正文时只注入这几个人的设定，出场名单必须与实际剧情一致。"
                + "集与集之间因果承接、冲突逐集升级。"
                + "直接输出中文可读文本，不要解释，不要 JSON，不要 Markdown 代码块。";
        String settingSystem = "你是一名资深短剧编剧兼分集策划。本次只输出三段设定，不要输出任何分集事件卡。"
                + "三个段的标题各自单独占一行（带书名号），段与段之间空一行，顺序固定："
                + "『故事简介』：120-200 字，写清主角是谁、核心冲突是什么、故事往哪走、结局是什么基调；不分集，不写事件卡。"
                + "『主要人物』：主要人物 5-8 人，每人一块，格式为"
                + "「姓名（年龄/性别）：身份与处境｜外貌与气质｜性格与说话方式｜核心动机与软肋｜成长线（从什么状态变成什么状态）」，"
                + "每人不少于 80 字，写成能直接写戏的具体描述，不要只堆标签，也不要把人物写成一模一样的模板。"
                + "『人物关系』：逐条写关键人物关系，每条格式为"
                + "「A ↔ B：关系性质（血亲/仇敌/师徒/暗恋/利益同盟等）+ 情感张力 + 藏着的事或未了的债」，"
                + "至少 6 条，覆盖主角与所有主要人物的关系，并交代关系链（谁欠谁、谁瞒谁、谁在等谁）。"
                + "直接输出中文可读文本，不要解释，不要 JSON，不要 Markdown 代码块。";
        String episodeSystem = base + "本次只输出分集事件卡，不要重复故事简介、主要人物与人物关系，直接从第1集开始编号。";
        String contSystem = base + "本次只输出指定集号区间的事件卡，不要重复故事简介、主要人物与人物关系，不要重写前情，"
                + "直接从指定集号开始编号。";

        // ---------- 阶段1：三块设定，独立一次调用 ----------
        String settingUser = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint
                + ledgerBlock(ledgerContext, ledgerClip)
                + "\n请输出故事简介、主要人物、人物关系三段设定。";
        AiCallResult setting = streamChat(appendSkill(settingSystem, skillContent), settingUser, onDelta,
                baseUrl, apiKey, model, settingMaxTokens);
        String settingText = setting.getContent();
        onDelta.accept("\n\n");
        StringBuilder all = new StringBuilder(settingText == null ? "" : settingText).append("\n\n");
        int totalTokens = setting.getTotalTokens();
        String modelName = setting.getModel();

        // ---------- 阶段2：分集事件卡，按批独立调用 ----------
        int total = parseTotalEpisodes(styleHint + "\n" + insp);
        int batchSize = 6;
        if (total <= 0)
        {
            // 未指定集数：与旧行为一致，单批输出，集数由模型决定
            String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint
                    + "\n【设定（已在上文输出，人物与关系必须与此一致）】\n" + clipText(settingText, settingClipFirst)
                    + ledgerBlock(ledgerContext, ledgerClip)
                    + "\n请从第1集开始输出分集事件卡。";
            AiCallResult r = streamChat(appendSkill(episodeSystem, skillContent), user, onDelta,
                    baseUrl, apiKey, model, episodeMaxTokens);
            onDelta.accept("\n\n");
            all.append(r.getContent()).append("\n\n");
            totalTokens += r.getTotalTokens();
            modelName = r.getModel();
            return new AiCallResult(normalizeEpisodeLines(all.toString().trim()), totalTokens,
                    modelName == null ? resolveModel(model) : modelName);
        }

        int start = 1;
        int guard = 0;
        int sameEpisodeRetry = 0;
        while (start <= total && guard++ < 40)
        {
            int end = Math.min(total, start + batchSize - 1);
            String user;
            if (start == 1)
            {
                user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint
                        + "\n【设定（已在上文输出，人物与关系必须与此一致）】\n" + clipText(settingText, settingClipFirst)
                        + ledgerBlock(ledgerContext, ledgerClip)
                        + "\n请输出第1集到第" + end + "集的事件卡（共 " + total + " 集，本次只写这一段）。";
            }
            else
            {
                // 带上一批最后 2 集的完整事件卡：只取 600 字会把倒数第二集的钩子切掉，跨批因果断开。
                String prev = lastEpisodes(all.toString(), 2, 1500);
                user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint
                        + "\n【前情结尾（勿重复）】\n" + prev
                        + "\n【设定摘要（人物与关系必须与此一致）】\n" + settingContinuation(settingText, settingClipCont)
                        + ledgerBlock(ledgerContext, ledgerClip)
                        + "\n请接着输出第" + start + "集到第" + end + "集的事件卡（共 " + total + " 集）。";
            }
            AiCallResult r = streamChat(appendSkill(start == 1 ? episodeSystem : contSystem, skillContent),
                    user, onDelta, baseUrl, apiKey, model, episodeMaxTokens);
            if (start > 1)
            {
                onDelta.accept("\n\n");
            }
            all.append(r.getContent()).append("\n\n");
            totalTokens += r.getTotalTokens();
            modelName = r.getModel();

            // 自适应续写：按实际输出的最大集号推进，避免模型少写时后面的集整段丢失。
            int reached = lastEpisodeNumber(r.getContent());
            if (reached < start)
            {
                throw new AiServiceUnavailableException("AI 未能输出第" + start + "集，请稍后重试");
            }
            // 末集可能被截断：丢掉残块，从这一集重写；同一集连续两次仍不完整则跳过，避免原地死循环。
            StringBuilder trimmed = trimIncompleteTail(all, reached);
            boolean tailComplete = trimmed.length() == all.length();
            all = trimmed;
            if (tailComplete)
            {
                sameEpisodeRetry = 0;
                start = reached + 1;
            }
            else if (sameEpisodeRetry++ >= 1)
            {
                sameEpisodeRetry = 0;
                start = reached + 1;
            }
            else
            {
                start = reached;
            }
        }
        return new AiCallResult(normalizeEpisodeLines(all.toString().trim()), totalTokens,
                modelName == null ? resolveModel(model) : modelName);
    }

    /**
     * 规整集号标题：把「第7集：」统一成独占一行的「第7集：」。
     * 模型偶尔会把下一集标题接在上一集钩子后面，前端按行解析会漏集。
     */
    static String normalizeEpisodeLines(String text)
    {
        if (text == null || text.isBlank())
        {
            return text == null ? "" : text;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("第\\s*(\\d{1,3})\\s*集\\s*[：:]").matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find())
        {
            String before = text.substring(last, m.start());
            out.append(before);
            if (out.length() > 0 && !before.endsWith("\n"))
            {
                out.append("\n");
            }
            out.append("第").append(m.group(1)).append("集：");
            last = m.end();
        }
        out.append(text.substring(last));
        return out.toString();
    }

    /** 取文本中行首出现的最大集号（"第N集"）；找不到返回 0。 */
    static int lastEpisodeNumber(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*第(\\d{1,3})集").matcher(text);
        int max = 0;
        while (m.find())
        {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    /** 若最后一集块没有"结尾钩子"（被截断），删掉该残块，等待下一批重写。 */
    static StringBuilder trimIncompleteTail(StringBuilder sb, int ep)
    {
        String s = sb.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*第" + ep + "集").matcher(s);
        int idx = -1;
        while (m.find())
        {
            idx = m.start();
        }
        if (idx < 0)
        {
            return sb;
        }
        String tail = s.substring(idx).trim();
        // 完整判据：写明钩子/悬念，或篇幅已足够且不是明显半句（模型未必按模板写"结尾钩子"）。
        boolean hasHook = tail.contains("钩子") || tail.contains("悬念") || tail.contains("反转");
        boolean longEnough = tail.length() >= 160 && !tail.endsWith("：") && !tail.endsWith("，");
        return (hasHook || longEnough) ? sb : new StringBuilder(s.substring(0, idx));
    }

    /** 从「共 30 集」「按 30 集」「30 集分集」等文本里取目标集数，取不到返回 0（按单批处理）。 */
    static int parseTotalEpisodes(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        // 明确写法：共 N 集 / N 集分集。必须先走这两条，避免把提示词模板里的「第1集：事件名」当成总集数。
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("共\\s*(\\d{1,3})\\s*集").matcher(text);
        if (m.find())
        {
            return clampEpisodes(m.group(1));
        }
        m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*集\\s*分集").matcher(text);
        if (m.find())
        {
            return clampEpisodes(m.group(1));
        }
        // 兜底：「写 N 集」「按 N 集」；跳过前面是「第」的集号（那是分集标题，不是总集数）。
        m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*集").matcher(text);
        while (m.find())
        {
            int at = m.start(1) - 1;
            while (at >= 0 && Character.isWhitespace(text.charAt(at)))
            {
                at--;
            }
            if (at >= 0 && text.charAt(at) == '第')
            {
                continue;
            }
            return clampEpisodes(m.group(1));
        }
        return 0;
    }

    /** 单部作品集数硬上限：50 集（三端输入同口径，防止提示词写出超额分批）。 */
    static final int MAX_EPISODES = 50;

    private static int clampEpisodes(String s)
    {
        return Math.min(Integer.parseInt(s), MAX_EPISODES);
    }

    /**
     * AI 分镜（流式）。[onDelta] 每收到一个内容增量回调一次（可读分镜文本）。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整分镜脚本，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult storyboardStream(String script, String style, String skillContent, Consumer<String> onDelta)
    {
        String styleHint = (style == null || style.isBlank()) ? "电影感" : style;
        String system = "你是一名资深影视分镜师，擅长把剧本转化为可直接用于拍摄或出售的专业分镜脚本。"
                + "请根据用户提供的剧本文本，按场景拆解为完整分镜；每场戏开头写清场次与场景地点，"
                + "每个镜头依次给出：镜号、景别、运镜、时长、画面内容、台词、音效/配乐、转场。"
                + "画面内容要具体到人物动作、表情与构图；台词直接引用或精简改写剧本对白；"
                + "整体风格：" + styleHint + "。直接用中文输出分镜正文，不要解释、不要前后缀。";
        String user = "剧本文本：\n" + script;
        return streamChat(appendSkill(system, skillContent), user, onDelta);
    }

    /** 截断文本到 max 字符（保留头部，null 视为空串）；用于设定原文（简介在前）这类「前面才是主线」的文本。 */
    private static String clipText(String text, int max)
    {
        if (text == null)
        {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * 取末尾 n 集的完整事件卡（按行首「第N集：」切块）。
     *
     * 续批前情只带 600 字会把倒数第二集的结果与钩子切掉，导致下一批接不上因果；
     * 这里按整集取，最多 max 字符兜底（集数越多、单集越短时不会被切半）。
     */
    static String lastEpisodes(String text, int n, int max)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*第\\s*\\d{1,3}\\s*集\\s*[：:]").matcher(text);
        java.util.List<Integer> starts = new java.util.ArrayList<>();
        while (m.find())
        {
            starts.add(m.start());
        }
        if (starts.isEmpty())
        {
            return clipText(text, max);
        }
        int from = starts.size() >= n ? starts.get(starts.size() - n) : starts.get(0);
        String block = text.substring(from).trim();
        return block.length() <= max ? block : clipText(block, max);
    }

    /**
     * 截断保留尾部（最新内容在尾部时使用）。
     * 台账是追加式、设定续批只有人物/关系有意义，取尾部才能拿到最新状态。
     */
    static String clipTail(String text, int max)
    {
        if (text == null)
        {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(trimmed.length() - max);
    }

    /**
     * 续批设定抽取：只保留『主要人物』与『人物关系』两段，各取尾部，合计不超过 max。
     *
     * 原实现从头部切 1200 字，只能拿到「故事简介」，人物关系整段丢失（实测 setting=200 时关系消失）。
     * 找不到分段标题时回退为整体尾部。
     */
    static String settingContinuation(String settingText, int max)
    {
        if (settingText == null || settingText.isBlank())
        {
            return "";
        }
        String text = settingText.trim();
        String characters = section(text, "主要人物");
        String relations = section(text, "人物关系");
        if (characters.isEmpty() && relations.isEmpty())
        {
            return clipTail(text, max);
        }
        // 关系与人物各占一半；缺失的一段额度让给另一段。
        int half = max / 2;
        String c = clipTail(characters, relations.isEmpty() ? max : half);
        String r = clipTail(relations, characters.isEmpty() ? max : max - c.length() - 1);
        StringBuilder sb = new StringBuilder();
        if (!c.isBlank())
        {
            sb.append("『主要人物』：").append(c);
        }
        if (!r.isBlank())
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append("『人物关系』：").append(r);
        }
        return sb.toString();
    }

    /** 取以『标题』开头的段落正文（到下一个『…』或结尾）。 */
    private static String section(String text, String title)
    {
        int from = text.indexOf("『" + title + "』");
        if (from < 0)
        {
            from = text.indexOf(title + "：");
            if (from < 0)
            {
                from = text.indexOf(title);
            }
        }
        if (from < 0)
        {
            return "";
        }
        int bodyStart = text.indexOf('』', from) >= 0 ? text.indexOf('』', from) + 1 : from + title.length();
        int next = text.indexOf("『", bodyStart);
        String body = text.substring(bodyStart, next > bodyStart ? next : text.length()).trim();
        if (body.startsWith("：") || body.startsWith(":"))
        {
            body = body.substring(1).trim();
        }
        return body;
    }

    /**
     * 台账注入块（按行取最新）：
     *
     * 台账每行是「标签：内容」，追加式，最新事实在尾部。原实现从头部截断，
     * 集数越多注入的越是第 1 集旧状态。这里改为从最后一行往前累加，
     * 超预算的那一行保留「标签：」前缀并标注前略，避免只剩半句没有归属。
     */
    static String clipLedgerLatest(String ledgerContext, int max)
    {
        if (ledgerContext == null || ledgerContext.isBlank())
        {
            return "";
        }
        String[] lines = ledgerContext.trim().split("\n");
        StringBuilder out = new StringBuilder();
        for (int i = lines.length - 1; i >= 0; i--)
        {
            String line = lines[i].trim();
            if (line.isEmpty())
            {
                continue;
            }
            String joined = out.length() == 0 ? line : line + "\n" + out;
            if (joined.length() <= max)
            {
                out.setLength(0);
                out.append(joined);
                continue;
            }
            int remain = max - out.length() - (out.length() == 0 ? 0 : 1);
            if (remain > 20)
            {
                String head = tailWithLabel(line, remain);
                out.insert(0, out.length() == 0 ? head : head + "\n");
            }
            break;
        }
        return out.toString();
    }

    /** 保留「标签：」前缀 + 内容尾部，前缀后标注（前略）。 */
    private static String tailWithLabel(String line, int budget)
    {
        int colon = line.indexOf('：');
        if (colon <= 0 || colon > 12)
        {
            return "（前略）" + clipTail(line, budget);
        }
        String label = line.substring(0, colon + 1);
        int contentBudget = budget - label.length() - 4;
        if (contentBudget <= 0)
        {
            return clipText(line, budget);
        }
        return label + "（前略）" + clipTail(line.substring(colon + 1), contentBudget);
    }

    /** 台账注入块：无内容时返回空串，非空时加标题并按「最新在尾部」截取。 */
    private static String ledgerBlock(String ledgerContext, int max)
    {
        if (ledgerContext == null || ledgerContext.isBlank())
        {
            return "";
        }
        return "\n【创作台账（已定事实，不得与之冲突）】\n" + clipLedgerLatest(ledgerContext, max);
    }

    /** 导演(skill) 指令追加到 system prompt 末尾（skill 内容非空时生效）。 */
    private static String appendSkill(String system, String skillContent)
    {
        if (skillContent == null || skillContent.isBlank())
        {
            return system;
        }
        return system + "\n\n【导演 skill 指令（最高优先级，必须严格遵守）】\n" + skillContent.trim();
    }

    /**
     * 流式调用 Chat Completions（stream=true），逐块解析 SSE 的 delta.content 并回调 [onDelta]。
     *
     * SSE 约定：每行 {@code data: {...}}，choices[0].delta.content 为增量；{@code data: [DONE]} 结束。
     * 逐行累积完整内容，结束时整体返回；DeepSeek 在最后一个 data 块携带 usage（total_tokens），
     * 逐块捕获，作为本次调用的精确 token 消耗用于按 token 扣积分。
     * 非 2xx / 无有效内容 / 网络异常抛 {@link AiServiceUnavailableException}。
     */
    /** 读取单次生成 token 上限；0 表示不限（请求不传 max_tokens）。配置不可用时回退 fallback。 */
    private int tokenLimit(String capability, int fallback)
    {
        if (genLimitConfig == null)
        {
            return fallback;
        }
        try
        {
            return genLimitConfig.maxTokens(capability);
        }
        catch (Exception e)
        {
            log.warn("读取生成 token 上限失败，使用默认 {}: {}", fallback, e.getMessage());
            return fallback;
        }
    }

    private AiCallResult streamChat(String system, String user, Consumer<String> onDelta)
    {
        return streamChat(system, user, onDelta, null, null, null);
    }

    /**
     * 流式调用（可按渠道覆盖 baseUrl / apiKey / model；传 null 用构造默认）。
     */
    private AiCallResult streamChat(String system, String user, Consumer<String> onDelta,
                                    String baseUrl, String apiKey, String model)
    {
        return streamChat(system, user, onDelta, baseUrl, apiKey, model, tokenLimit("write", 8192));
    }

    /** 流式调用（可指定本次输出额度：设定块与事件卡各用各的 token 预算）。 */
    private AiCallResult streamChat(String system, String user, Consumer<String> onDelta,
                                    String baseUrl, String apiKey, String model, int maxTokens)
    {
        String url = resolveBaseUrl(baseUrl);
        String key = resolveApiKey(apiKey);
        String resolvedModel = resolveModel(model);
        if (key.isBlank())
        {
            throw new AiServiceUnavailableException("AI 服务未配置，请联系管理员");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", user));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolvedModel);
        body.put("messages", messages);
        // 0 = 不限：不传 max_tokens，交给模型默认上限。
        if (maxTokens > 0)
        {
            body.put("max_tokens", maxTokens);
        }
        body.put("temperature", 0.8);
        body.put("stream", true);
        try
        {
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/v1/chat/completions"))
                    .timeout(java.time.Duration.ofSeconds(600))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = streamClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试 (HTTP " + response.statusCode() + ")");
            }
            StringBuilder full = new StringBuilder();
            boolean completed = false;
            // length 表示输出被长度上限截断，正文常停在半句，必须如实上报
            boolean truncated = false;
            int totalTokens = 0;
            String modelName = resolvedModel;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.isBlank())
                    {
                        continue;
                    }
                    if (!line.startsWith("data:"))
                    {
                        continue; // 忽略注释/事件行
                    }
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]"))
                    {
                        completed = true;
                        break;
                    }
                    int usage = parseSseUsage(data);
                    if (usage > 0)
                    {
                        totalTokens = usage; // 最后一个带 usage 的块为准（DeepSeek 流式最后一条）
                    }
                    // 注意：不回填 SSE 响应里的 model（DeepSeek 会回显别名如 deepseek-flash），
                    // 落库统一用请求模型名，保证记录展示与用户选择一致。
                    String delta = parseSseDelta(data);
                    if (delta != null && !delta.isEmpty())
                    {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                    String finishReason = parseSseFinishReason(data);
                    if (finishReason != null)
                    {
                        completed = true;
                        truncated = "length".equals(finishReason);
                        break;
                    }
                }
            }
            String content = full.toString().trim();
            if (content.isEmpty())
            {
                // 推理模型（如 deepseek-flash）的流式响应会先输出 reasoning_content，
                // 若思维链耗尽预算，正文 content 会为空。此时回退到非流式请求拿正文，避免用户看到失败。
                log.warn("AI stream returned empty content, fallback to non-stream call");
                AiCallResult fallback = chat(system, user, baseUrl, apiKey, model);
                onDelta.accept(fallback.getContent());
                return fallback;
            }
            if (!completed)
            {
                throw new AiServiceUnavailableException("AI 生成中断，请稍后重试");
            }
            return new AiCallResult(content, totalTokens, modelName, truncated);
        }
        catch (AiServiceUnavailableException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("AI stream chat call failed: {}", e.toString(), e);
            throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试");
        }
    }

    /**
     * 解析单条 SSE data（不含 data: 前缀）的 delta.content。
     * 无法解析的块返回 null（调用方跳过）；无增量内容返回空串。
     */
    static String parseSseDelta(String data)
    {
        if (data == null || data.isEmpty() || data.equals("[DONE]"))
        {
            return "";
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            String delta = root.path("choices").path(0).path("delta").path("content").asText(null);
            return delta == null ? "" : delta;
        }
        catch (Exception e)
        {
            return null; // 无法解析的中间块，调用方跳过
        }
    }

    /** 是否 SSE 终止块（finish_reason=stop/length）。 */
    static boolean isSseFinish(String data)
    {
        return parseSseFinishReason(data) != null;
    }

    /**
     * 解析 SSE 块的 finish_reason（取不到返回 null）。
     *
     * stop=正常结束；length=输出被长度上限截断（正文常停在半句，需提示重试）。
     */
    static String parseSseFinishReason(String data)
    {
        if (data == null || data.isEmpty())
        {
            return null;
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            String reason = root.path("choices").path(0).path("finish_reason").asText("");
            if ("stop".equals(reason) || "length".equals(reason))
            {
                return reason;
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 从单条 SSE data 解析本次调用的 usage.total_tokens（DeepSeek 流式在最后一个 data 块携带 usage）。
     * 无 usage / 无法解析返回 0。
     */
    static int parseSseUsage(String data)
    {
        if (data == null || data.isEmpty() || data.equals("[DONE]"))
        {
            return 0;
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            int total = root.path("usage").path("total_tokens").asInt(0);
            return total > 0 ? total : 0;
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /** 从单条 SSE data 解析模型名；缺失/无法解析返回 null。 */
    static String parseSseModel(String data)
    {
        if (data == null || data.isEmpty() || data.equals("[DONE]"))
        {
            return null;
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            String m = root.path("model").asText(null);
            return (m == null || m.isBlank()) ? null : m;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 非流式调用（结构化输出场景，如「本章摘要 + 台账变更」需要一次性拿完整 JSON）。
     * 用于不需要打字机效果、但对完整性与可解析性要求更高的调用。
     */
    public AiCallResult chatText(String system, String user)
    {
        return chat(system, user, null, null, null);
    }

    /** 同上，可按渠道覆盖 baseUrl / apiKey / model（传 null 用构造默认）。 */
    public AiCallResult chatText(String system, String user, String baseUrl, String apiKey, String model)
    {
        return chat(system, user, baseUrl, apiKey, model);
    }

    /**
     * 调用 Chat Completions（非流式），返回完整内容与本次 token 消耗。
     */
    private AiCallResult chat(String system, String user)
    {
        return chat(system, user, null, null, null);
    }

    /**
     * 调用 Chat Completions（可按渠道覆盖 baseUrl / apiKey / model；传 null 用构造默认）。
     */
    private AiCallResult chat(String system, String user, String baseUrl, String apiKey, String model)
    {
        String url = resolveBaseUrl(baseUrl);
        String key = resolveApiKey(apiKey);
        String m = resolveModel(model);
        if (key.isBlank())
        {
            throw new AiServiceUnavailableException("AI 服务未配置，请联系管理员");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", user));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", m);
        body.put("messages", messages);
        int maxTokens = tokenLimit("write", 8192);
        if (maxTokens > 0)
        {
            body.put("max_tokens", maxTokens);
        }
        body.put("temperature", 0.8);
        body.put("stream", false);
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url + "/v1/chat/completions",
                    new HttpEntity<>(body, headers), String.class);
            String raw = response.getBody();
            if (raw == null || raw.isBlank())
            {
                throw new AiServiceUnavailableException("AI 返回为空，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content == null || content.isBlank())
            {
                throw new AiServiceUnavailableException("AI 未返回有效内容，请稍后重试");
            }
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);
            // 落库统一用请求模型名（DeepSeek 响应 model 会回显别名，如 deepseek-chat → deepseek-flash）
            String modelName = m;
            boolean truncated = "length".equals(
                    root.path("choices").path(0).path("finish_reason").asText(""));
            return new AiCallResult(content.trim(), totalTokens, modelName, truncated);
        }
        catch (AiServiceUnavailableException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("AI chat call failed: {}", e.toString(), e);
            throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试");
        }
    }

    // ================================================================
    // 渠道参数覆盖：按管理员配置的 AI 渠道（baseUrl / apiKey / model）调用。
    // 与无参版本语义一致，仅 URL / 密钥 / 模型取自渠道；传 null 时回退构造默认。
    // ================================================================

    public AiCallResult write(String prompt, String type, String skillContent, String baseUrl, String apiKey, String model)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深网络小说写手，擅长创作节奏紧凑、画面感强、对话自然的小说正文。"
                + "请根据用户的要求直接创作小说正文，不要解释、不要标题、不要任何前后缀。";
        return chat(appendSkill(system, skillContent), prompt + typeHint, baseUrl, apiKey, model);
    }

    public AiCallResult polish(String content, String style, String skillContent, String baseUrl, String apiKey, String model)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        return chat(appendSkill(system, skillContent), content, baseUrl, apiKey, model);
    }

    public AiCallResult outline(String inspiration, String genre, String style, String skillContent, String baseUrl, String apiKey, String model)
    {
        String genreHint = (genre == null || genre.isBlank()) ? "不限" : genre;
        String styleHint = (style == null || style.isBlank()) ? "不限" : style;
        String insp = (inspiration == null || inspiration.isBlank()) ? "" : inspiration;
        // 一键生成整部：风格里写明「共 N 集」时按集出稿，否则沿用分幕结构（不影响其他入口）。
        Matcher episodeMatcher = EPISODE_COUNT.matcher(styleHint);
        if (episodeMatcher.find())
        {
            int count = Math.max(1, Math.min(80, Integer.parseInt(episodeMatcher.group(1))));
            String system = "你是一名资深编剧，擅长帮完全不懂创作的普通人把题材直接拆成可拍的分集大纲。"
                    + "请根据用户给的灵感、题材与集数，输出严格 " + count + " 集，集号从 1 到 " + count + " 连续不间断，不要合并或跳号。"
                    + "必须只输出一个合法 JSON 对象，不要解释，不要使用 Markdown 代码块。JSON 结构如下："
                    + "{\"title\":\"作品名\",\"logline\":\"一句话故事\","
                    + "\"characters\":[{\"name\":\"角色名\",\"role\":\"身份或定位\"}],"
                    + "\"episodes\":[{\"no\":1,\"title\":\"本集事件名（不超过12字）\","
                    + "\"card\":\"本集事件 / 冲突与推进 / 结果或反转 / 结尾钩子，80-150字\"}]}";
            String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n集数：" + count + " 集";
            return chat(appendSkill(system, skillContent), user, baseUrl, apiKey, model);
        }
        String system = "你是一名资深网文策划编辑，擅长帮完全不懂创作的普通人快速产出专业级剧本大纲。"
                + "请根据用户给出的灵感、题材与风格，生成一份结构完整、可直接展开成剧本的小说大纲；"
                + "要求用通俗易懂的语言，每个分幕写清推进逻辑与关键冲突，让新手照着写也能写成不错的故事。"
                + "必须只输出一个合法 JSON 对象，不要输出任何解释，也不要使用 Markdown 代码块。"
                + "JSON 结构如下："
                + "{\"title\":\"作品名\",\"logline\":\"一句话故事\","
                + "\"characters\":[{\"name\":\"角色名\",\"role\":\"身份或定位\"}],"
                + "\"acts\":[{\"name\":\"第一幕\",\"summary\":\"本幕概述\",\"beats\":[\"关键情节1\",\"关键情节2\"]}]}";
        String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint;
        return chat(appendSkill(system, skillContent), user, baseUrl, apiKey, model);
    }

    public AiCallResult storyboard(String script, String style, String skillContent, String baseUrl, String apiKey, String model)
    {
        String styleHint = (style == null || style.isBlank()) ? "电影感" : style;
        String system = "你是一名资深影视分镜师，擅长把剧本转化为可直接用于拍摄或出售的专业分镜脚本。"
                + "请根据用户提供的剧本文本，按场景拆解为完整分镜；每场戏开头写清场次与场景地点，"
                + "每个镜头依次给出：镜号、景别、运镜、时长、画面内容、台词、音效/配乐、转场。"
                + "画面内容要具体到人物动作、表情与构图；台词直接引用或精简改写剧本对白；"
                + "整体风格：" + styleHint + "。直接用中文输出分镜正文，不要解释、不要前后缀。";
        String user = "剧本文本：\n" + script;
        return chat(appendSkill(system, skillContent), user, baseUrl, apiKey, model);
    }

    public AiCallResult writeStream(String prompt, String type, String skillContent, Consumer<String> onDelta,
                                    String baseUrl, String apiKey, String model)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深短剧编剧，擅长把分集事件写成一集可直接排演的剧本正文。"
                + "写法要求：开头一句自然交代本集发生的地点和时间（如“顾家老宅的院子里，午后的日头正毒”），"
                + "不要写“场景1”“内景/外景”这类场景头，也不要写镜号、景别、运镜、转场，这些交给分镜环节；"
                + "正文以人物动作和对白推进，对白用“角色：台词”格式，句子短、有来有回；"
                + "篇幅 700-900 字，只写这一集，本集要有完整结果，结尾留一个具体钩子（一个动作、一句关键台词或一个未解的画面）；"
                + "不要解释、不要章节标题、不要任何前后缀。";
        return streamChat(appendSkill(system, skillContent), prompt + typeHint, onDelta, baseUrl, apiKey, model);
    }

    public AiCallResult polishStream(String content, String style, String skillContent, Consumer<String> onDelta,
                                     String baseUrl, String apiKey, String model)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        return streamChat(appendSkill(system, skillContent), content, onDelta, baseUrl, apiKey, model);
    }

    public AiCallResult outlineStream(String inspiration, String genre, String style, String skillContent, Consumer<String> onDelta,
                                      String baseUrl, String apiKey, String model)
    {
        return outlineStreamBatched(inspiration, genre, style, skillContent, null, onDelta, baseUrl, apiKey, model);
    }

    /** 同上，带创作台账（PC/App 传作品台账文本）。 */
    public AiCallResult outlineStream(String inspiration, String genre, String style, String skillContent,
                                      String ledgerContext, Consumer<String> onDelta,
                                      String baseUrl, String apiKey, String model)
    {
        return outlineStreamBatched(inspiration, genre, style, skillContent, ledgerContext, onDelta,
                baseUrl, apiKey, model);
    }

    public AiCallResult storyboardStream(String script, String style, String skillContent, Consumer<String> onDelta,
                                         String baseUrl, String apiKey, String model)
    {
        String styleHint = (style == null || style.isBlank()) ? "电影感" : style;
        String system = "你是一名资深影视分镜师，擅长把剧本转化为可直接用于拍摄或出售的专业分镜脚本。"
                + "请根据用户提供的剧本文本，按场景拆解为完整分镜；每场戏开头写清场次与场景地点，"
                + "每个镜头依次给出：镜号、景别、运镜、时长、画面内容、台词、音效/配乐、转场。"
                + "画面内容要具体到人物动作、表情与构图；台词直接引用或精简改写剧本对白；"
                + "整体风格：" + styleHint + "。直接用中文输出分镜正文，不要解释、不要前后缀。";
        String user = "剧本文本：\n" + script;
        return streamChat(appendSkill(system, skillContent), user, onDelta, baseUrl, apiKey, model);
    }

    /** 渠道 baseUrl 覆盖（null/空白 → 构造默认） */
    private String resolveBaseUrl(String baseUrl)
    {
        return (baseUrl == null || baseUrl.isBlank()) ? this.baseUrl : trimTrailingSlash(baseUrl);
    }

    /** 渠道 apiKey 覆盖（null/空白 → 构造默认） */
    private String resolveApiKey(String apiKey)
    {
        return (apiKey == null || apiKey.isBlank()) ? this.apiKey : apiKey.trim();
    }

    /** 渠道 model 覆盖（null/空白 → 构造默认） */
    private String resolveModel(String model)
    {
        return (model == null || model.isBlank()) ? this.model : model.trim();
    }

    /**
     * 解析模型返回的 JSON；去除可能的 Markdown 代码块包裹。
     * 若仍不是合法 JSON，兜底把整段文本放入 text，避免编造结构。
     */
    JsonNode parseJson(String raw)
    {
        String s = raw.trim();
        if (s.startsWith("```"))
        {
            int newline = s.indexOf('\n');
            if (newline >= 0)
            {
                s = s.substring(newline + 1);
            }
            if (s.endsWith("```"))
            {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        try
        {
            return objectMapper.readTree(s);
        }
        catch (Exception e)
        {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("text", raw);
            return fallback;
        }
    }

    private static Map<String, String> message(String role, String content)
    {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static String trimTrailingSlash(String url)
    {
        if (url == null)
        {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
