package cn.zencode.assist.core.webchecks;

import java.util.*;
import java.util.regex.*;

/** Bounded markup hints, never a substitute for an HTML/JSP parser. */
public final class WebChecks {
    private WebChecks() { }
    public static final int MAX_LENGTH = 1000000;
    public static List<String> check(String extension, String source) {
        List<String> results = new ArrayList<String>();
        if (source.length() > MAX_LENGTH) {
            results.add("文件超过 1,000,000 字符，未执行检查。"); return results;
        }
        if (!("html".equalsIgnoreCase(extension) || "htm".equalsIgnoreCase(extension) || "jsp".equalsIgnoreCase(extension))) {
            results.add("当前仅检查 HTML/JSP 的静态重复 id；CSS/JavaScript 语法尚未检查。"); return results;
        }
        // Suppress template files entirely: conditional branches and generated markup require evaluation.
        if (source.contains("<%") || source.contains("${") || source.contains("#{") || source.contains("<c:")) {
            results.add("包含动态模板，未检查重复 id（需要运行时上下文）。"); return results;
        }
        String cleaned = source.replaceAll("(?s)<!--.*?(?:-->|$)", " ")
            .replaceAll("(?is)<(script|style)\\b[^>]*>.*?(?:</\\1\\s*>|$)", " ");
        Pattern tag = Pattern.compile("<[A-Za-z][A-Za-z0-9:-]*(?:\\s+[A-Za-z_:][A-Za-z0-9_.:-]*(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*\\s*/?>");
        Pattern attribute = Pattern.compile("([A-Za-z_:][A-Za-z0-9_.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
        Set<String> seen = new HashSet<String>();
        Matcher tags = tag.matcher(cleaned);
        while (tags.find()) {
            Matcher attrs = attribute.matcher(tags.group());
            while (attrs.find()) {
                if (!"id".equalsIgnoreCase(attrs.group(1))) continue;
                String value = attrs.group(2) != null ? attrs.group(2) : attrs.group(3) != null ? attrs.group(3) : attrs.group(4);
                if (value.isEmpty() || value.contains("&")) continue;
                if (!seen.add(value)) results.add("重复静态 id：" + value + "；请核对是否同时出现在页面中。");
            }
        }
        if (results.isEmpty()) results.add("未发现重复静态 id。仅检查完整开始标签；未执行完整语法、资源路径或跨文件检查。");
        return Collections.unmodifiableList(results);
    }
}
