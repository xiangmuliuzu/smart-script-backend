package com.smartscript.platform.content.controller.app;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.service.IAppChapterAuthorService;
import com.smartscript.platform.content.service.IAppWorkLedgerService;

/**
 * 作品创作台账读写（PC 用户端与 App 共用）
 *
 * 路径：/api/v1/content/works/{workId}/ledger
 *   GET  查询台账（不存在返回仅含 workId 的空对象）
 *   PUT  整条覆盖保存
 *
 * 鉴权：App 凭证域私有接口，需登录；作品作者必须为当前身份，
 *       非本人 403、作品不存在 404（与章节编辑同一套归属判定）。
 *
 * 台账七类：故事简介、主要人物、人物关系、伏笔、时间线、物品位置、未解线索。
 *
 * @author smartscript
 */
@RestController
@PreAuthorize("@contentAuthorAccess.allowed()")
@RequestMapping("/api/v1/content")
public class AppWorkLedgerController
{
    /** 作品不存在或已删除 */
    private static final int CODE_NOT_FOUND = 404;

    /** 非本人作品，无权操作 */
    private static final int CODE_FORBIDDEN = 403;

    /** 入参非法（单字段超长） */
    private static final int CODE_BAD_REQUEST = 400;

    /** 故事简介长度上限 */
    private static final int SUMMARY_MAX_LENGTH = 2000;

    /** 其余台账单字段长度上限 */
    private static final int FIELD_MAX_LENGTH = 8000;

    private final IAppChapterAuthorService chapterService;

    private final IAppWorkLedgerService ledgerService;

    public AppWorkLedgerController(IAppChapterAuthorService chapterService,
            IAppWorkLedgerService ledgerService)
    {
        this.chapterService = chapterService;
        this.ledgerService = ledgerService;
    }

    /**
     * 查询作品台账
     *
     * @param workId 作品ID
     * @return data = 台账对象；非本人 403，作品不存在/已删除 404
     */
    @GetMapping("/works/{workId}/ledger")
    public AppApiResponse<SysWorkLedger> get(@PathVariable Long workId)
    {
        int access = chapterService.resolveWorkAccess(workId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该作品");
        }
        return AppApiResponse.ok(ledgerService.getLedger(workId));
    }

    /**
     * 保存作品台账（整条覆盖）
     *
     * @param workId 作品ID
     * @param ledger body：storySummary/characters/relations/foreshadow/timeline/items/threads
     * @return data = null；非本人 403，作品不存在 404，单字段超长 400
     */
    @PutMapping("/works/{workId}/ledger")
    public AppApiResponse<Void> save(@PathVariable Long workId,
            @RequestBody(required = false) SysWorkLedger ledger)
    {
        int access = chapterService.resolveWorkAccess(workId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该作品");
        }
        if (ledger == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "台账内容不能为空");
        }
        if (overLength(ledger.getStorySummary(), SUMMARY_MAX_LENGTH)
                || overLength(ledger.getCharacters(), FIELD_MAX_LENGTH)
                || overLength(ledger.getRelations(), FIELD_MAX_LENGTH)
                || overLength(ledger.getForeshadow(), FIELD_MAX_LENGTH)
                || overLength(ledger.getTimeline(), FIELD_MAX_LENGTH)
                || overLength(ledger.getItems(), FIELD_MAX_LENGTH)
                || overLength(ledger.getThreads(), FIELD_MAX_LENGTH))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "台账内容过长，请精简后重试");
        }
        if (!ledgerService.saveLedger(workId, ledger))
        {
            return AppApiResponse.fail(500, "保存失败，请稍后重试");
        }
        return AppApiResponse.ok();
    }

    /** 字段是否超过上限（null 视为未传，不算超长） */
    private static boolean overLength(String value, int max)
    {
        return value != null && value.length() > max;
    }
}
