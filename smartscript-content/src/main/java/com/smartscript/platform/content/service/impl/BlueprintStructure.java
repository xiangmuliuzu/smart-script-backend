package com.smartscript.platform.content.service.impl;

import static com.smartscript.platform.content.service.impl.StructuredWritingJson.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.domain.SysWorkLedger;

final class BlueprintStructure
{
    private static final String[] CHARACTER_FIELDS = {"role", "appearance", "trait", "goal", "growth", "secret"};

    private BlueprintStructure() { }

    static String schema(String section)
    {
        return switch (section)
        {
            case "storyCore" -> "storyCore: {logline, summary, coreConflict, narrativeStyle, contentBoundary, endingTone}，值为文字，故事简介不超过 500 字。";
            case "world" -> "world: {setting, rules:[{name,detail}], constraints:[文字]}。";
            case "outline" -> "outline: {mainline, acts:[{name,range,summary,beats:[文字]}], endingHanger}，acts 恰好三幕，beats 是幕级节拍。";
            case "characters" -> "characters: [{key?, name, role, age, gender, appearance, trait, goal, growth, secret}]。";
            case "items" -> "items: [{key?, name, desc, owner, count, state}]，件数为文字，使用作者设定。";
            case "relations" -> "relations: [{a,b,label,dash}]，a/b 为主要人物姓名或 key，dash 为布尔值。";
            default -> throw new IllegalArgumentException("未知作品资料类型");
        };
    }

    static ObjectNode normalize(ObjectNode source, List<String> sections)
    {
        ObjectNode blueprint = object();
        for (String section : sections)
        {
            merge(blueprint, section, null, source.path(section));
        }
        return blueprint;
    }

    static void merge(ObjectNode target, String section, String key, JsonNode source)
    {
        JsonNode updated = switch (section)
        {
            case "storyCore" -> story(source);
            case "world" -> world(source);
            case "outline" -> outline(source);
            case "characters", "items" -> namedEntries(source, target.path(section), section, key);
            case "relations" -> edges(source, target.path("characters"));
            default -> throw new IllegalArgumentException("未知作品资料类型");
        };
        target.set(section, updated);
    }

    static void mergeRoster(ObjectNode target, ObjectNode source, ObjectNode previous)
    {
        for (String section : List.of("characters", "items"))
        {
            target.set(section, namedEntries(source.path(section), previous.path(section), section, null));
        }
        target.set("relations", edges(source.path("relations"), target.path("characters"), source.path("characters")));
    }

    private static ObjectNode story(JsonNode source)
    {
        ObjectNode story = textFields(source, "logline", "summary", "coreConflict", "narrativeStyle", "contentBoundary", "endingTone");
        if (!story.has("logline") && !story.has("summary"))
        {
            throw invalidResult();
        }
        if (story.has("summary"))
        {
            story.put("summary", limit(text(story.path("summary")), 500));
        }
        return story;
    }

    private static ObjectNode world(JsonNode source)
    {
        if (!source.isObject())
        {
            throw invalidResult();
        }
        ObjectNode world = textFields(source, "setting");
        ArrayNode rules = world.putArray("rules");
        for (JsonNode rule : source.path("rules"))
        {
            ObjectNode fields = textFields(rule, "name", "detail");
            if (!fields.isEmpty())
            {
                rules.add(fields);
            }
        }
        world.set("constraints", textArray(source.path("constraints")));
        return world;
    }

    private static ObjectNode outline(JsonNode source)
    {
        ArrayNode input = array(source.path("acts"));
        if (input.size() != 3)
        {
            throw invalidResult();
        }
        ObjectNode outline = textFields(source, "mainline", "endingHanger");
        ArrayNode acts = outline.putArray("acts");
        for (JsonNode act : input)
        {
            ObjectNode fields = textFields(act, "name", "range", "summary");
            ArrayNode beats = textArray(act.path("beats"));
            if (!fields.has("summary") && beats.isEmpty())
            {
                throw invalidResult();
            }
            fields.set("beats", beats);
            acts.add(fields);
        }
        return outline;
    }

    private static ArrayNode namedEntries(JsonNode source, JsonNode old, String section, String key)
    {
        ArrayNode input = array(source);
        boolean character = "characters".equals(section);
        if (key != null)
        {
            JsonNode previous = BlueprintService.findEntry(old, key);
            if (input.size() != 1 || !input.get(0).isObject())
            {
                throw invalidResult();
            }
            ObjectNode fields = entryFields(input.get(0), character);
            if (fields.isEmpty())
            {
                throw invalidResult();
            }
            ObjectNode entry = ((ObjectNode) previous).deepCopy().setAll(fields);
            for (JsonNode other : old)
            {
                if (other != previous && text(other.path("name")).equals(text(entry.path("name"))))
                {
                    throw invalidResult();
                }
            }
            ArrayNode replaced = object().arrayNode();
            old.forEach(item -> replaced.add(item == previous ? entry : item));
            return replaced;
        }

        ArrayNode entries = object().arrayNode();
        Map<String, JsonNode> previous = index(old, "name");
        Set<String> names = new HashSet<>();
        Set<String> keys = new HashSet<>(index(old, "key").keySet());
        for (JsonNode item : input)
        {
            ObjectNode fields = entryFields(item, character);
            String name = text(fields.path("name"));
            if (name.isEmpty() || !names.add(name))
            {
                continue;
            }
            JsonNode prior = previous.get(name);
            String id = prior == null ? nameKey(character ? "c" : "i", name) : text(prior.path("key"));
            while (prior == null && !keys.add(id))
            {
                id += "x";
            }
            fields.put("key", id);
            entries.add(fields);
        }
        if (!input.isEmpty() && entries.isEmpty())
        {
            throw invalidResult();
        }
        return entries;
    }

    private static ObjectNode entryFields(JsonNode item, boolean character)
    {
        ObjectNode fields = character
                ? textFields(item, "name", "role", "age", "gender", "appearance", "trait", "goal", "growth", "secret")
                : textFields(item, "name", "icon", "desc", "owner", "count", "state");
        if (!character && !fields.has("count") && item.path("count").isNumber())
        {
            fields.put("count", item.path("count").asText());
        }
        return fields;
    }

    static ArrayNode edges(JsonNode source, JsonNode characters)
    {
        return edges(source, characters, object().arrayNode());
    }

    private static ArrayNode edges(JsonNode source, JsonNode characters, JsonNode aliases)
    {
        Map<String, JsonNode> byKey = index(characters, "key");
        Map<String, JsonNode> byName = index(characters, "name");
        index(aliases, "key").forEach((key, alias) -> {
            JsonNode canonical = byName.get(text(alias.path("name")));
            if (canonical != null)
            {
                byKey.putIfAbsent(key, canonical);
            }
        });
        Set<Set<String>> pairs = new HashSet<>();
        ArrayNode edges = object().arrayNode();
        for (JsonNode edge : array(source))
        {
            String a = resolveKey(text(edge.path("a")), byKey, byName);
            String b = resolveKey(text(edge.path("b")), byKey, byName);
            String label = text(edge.path("label"));
            if (!a.isEmpty() && !b.isEmpty() && !a.equals(b) && !label.isEmpty() && pairs.add(Set.of(a, b)))
            {
                edges.addObject().put("a", a).put("b", b).put("label", label).put("dash", edge.path("dash").asBoolean(false));
            }
        }
        return edges;
    }

    private static String resolveKey(String value, Map<String, JsonNode> byKey, Map<String, JsonNode> byName)
    {
        JsonNode resolved = byKey.getOrDefault(value, byName.get(value));
        return resolved == null ? "" : text(resolved.path("key"));
    }

    static Map<String, JsonNode> index(JsonNode entries, String field)
    {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode entry : entries)
        {
            String value = text(entry.path(field));
            if (!value.isEmpty())
            {
                result.putIfAbsent(value, entry);
            }
        }
        return result;
    }

    static ArrayNode array(JsonNode value)
    {
        if (value instanceof ArrayNode array)
        {
            return array;
        }
        throw invalidResult();
    }

    private static ArrayNode textArray(JsonNode input)
    {
        ArrayNode result = object().arrayNode();
        for (JsonNode value : input)
        {
            String text = text(value);
            if (!text.isEmpty())
            {
                result.add(text);
            }
        }
        return result;
    }

    static String summary(JsonNode blueprint)
    {
        JsonNode story = blueprint.path("storyCore");
        String summary = text(story.path("summary"));
        return summary.isEmpty() ? limit(text(story.path("logline")), 500) : summary;
    }

    static SysWorkLedger ledger(ObjectNode blueprint)
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setStorySummary(summary(blueprint));
        JsonNode story = blueprint.path("storyCore");
        JsonNode world = blueprint.path("world");
        JsonNode outline = blueprint.path("outline");
        StringJoiner characters = new StringJoiner("\n");
        for (JsonNode character : blueprint.path("characters"))
        {
            String identity = String.join("/", List.of(text(character.path("age")), text(character.path("gender"))));
            StringJoiner details = new StringJoiner("｜");
            for (String field : CHARACTER_FIELDS)
            {
                details.add(oneLine(character.path(field)));
            }
            characters.add(oneLine(character.path("name")) + ("/".equals(identity) ? "" : "（" + identity + "）") + "：" + details);
        }
        ledger.setCharacters(characters.toString());
        Map<String, JsonNode> byKey = index(blueprint.path("characters"), "key");
        StringJoiner relations = new StringJoiner("\n");
        for (JsonNode edge : blueprint.path("relations"))
        {
            JsonNode a = byKey.get(text(edge.path("a")));
            JsonNode b = byKey.get(text(edge.path("b")));
            if (a != null && b != null)
            {
                relations.add(oneLine(a.path("name")) + " ↔ " + oneLine(b.path("name")) + "：" + oneLine(edge.path("label")));
            }
        }
        ledger.setRelations(relations.toString());
        StringJoiner items = new StringJoiner("\n");
        StringJoiner props = new StringJoiner("\n");
        for (JsonNode item : blueprint.path("items"))
        {
            String name = oneLine(item.path("name"));
            items.add(name + "：" + oneLine(item.path("state")));
            props.add(String.join("｜", name, oneLine(item.path("count")), oneLine(item.path("owner")),
                    oneLine(item.path("state")), oneLine(item.path("desc"))));
        }
        ledger.setItems(items.toString());
        ledger.setProps(props.toString());
        ledger.setStyleGuide(joinText(story.path("narrativeStyle"), story.path("contentBoundary")));
        ledger.setForeshadow(joinText(world.path("constraints")));
        ledger.setThreads(ledger.getForeshadow());
        StringJoiner acts = new StringJoiner("\n");
        addLine(acts, "故事主线：", text(outline.path("mainline")));
        int number = 0;
        for (JsonNode act : outline.path("acts"))
        {
            String name = oneLine(act.path("name"));
            String range = oneLine(act.path("range"));
            number++;
            if (name.isEmpty())
            {
                name = List.of("第一幕", "第二幕", "第三幕").get(number - 1);
            }
            String description = oneLine(act.path("summary"));
            String beats = joinText(act.path("beats")).replace("\n", "；");
            acts.add(name + (range.isEmpty() ? "" : "（" + range + "）") + "：" + description
                    + (beats.isEmpty() ? "" : (description.isEmpty() ? "" : "。") + "节拍：" + beats));
        }
        addLine(acts, "结尾悬念：", text(outline.path("endingHanger")));
        ledger.setOutline(acts.toString());
        StringJoiner state = new StringJoiner("\n");
        state.add("计划集数=" + blueprint.path("planEpisodes").asInt() + " 集；单集时长="
                + blueprint.path("minutesPerEpisode").decimalValue().stripTrailingZeros().toPlainString() + " 分钟。");
        addLine(state, "核心冲突：", text(story.path("coreConflict")));
        addLine(state, "世界观：", text(world.path("setting")));
        for (JsonNode rule : world.path("rules"))
        {
            addLine(state, oneLine(rule.path("name")) + "：", oneLine(rule.path("detail")));
        }
        addLine(state, "结局基调：", text(story.path("endingTone")));
        ledger.setStateBoard(state.toString());
        return ledger;
    }

    private static String oneLine(JsonNode value)
    {
        return text(value).replaceAll("[\\r\\n]+", " ");
    }

    private static String joinText(JsonNode... values)
    {
        StringJoiner lines = new StringJoiner("\n");
        for (JsonNode value : values)
        {
            if (value.isArray())
            {
                value.forEach(item -> addLine(lines, "", text(item)));
            }
            else
            {
                addLine(lines, "", text(value));
            }
        }
        return lines.toString();
    }

    private static void addLine(StringJoiner lines, String prefix, String text)
    {
        if (!text.isEmpty())
        {
            lines.add(prefix + text);
        }
    }
}
