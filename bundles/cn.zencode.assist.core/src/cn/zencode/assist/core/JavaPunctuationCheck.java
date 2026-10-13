package cn.zencode.assist.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conservative Java 8 lexical check; not a JSP/JS parser or a compiler replacement. */
public final class JavaPunctuationCheck {
    private static final String FULL = "；，（）｛｝［］＝：";
    private static final String ASCII = ";,(){}[]=:";
    private enum State { CODE, STRING, CHARACTER, LINE_COMMENT, BLOCK_COMMENT }

    public List<Diagnostic> check(String source) {
        // Java translates Unicode escapes before tokenization. Defer these files to JDT
        // rather than misclassify escaped quotes, comment delimiters or newlines.
        if (source.contains("\\u")) return Collections.emptyList();
        List<Diagnostic> result = new ArrayList<Diagnostic>();
        State state = State.CODE;
        int line = 1;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (c == '\r' || (c == '\n' && (i == 0 || source.charAt(i - 1) != '\r'))) line++;
            if (state == State.LINE_COMMENT) {
                if (c == '\n' || c == '\r') state = State.CODE;
                continue;
            }
            if (state == State.BLOCK_COMMENT) {
                if (c == '*' && next == '/') { state = State.CODE; i++; }
                continue;
            }
            if (state == State.STRING || state == State.CHARACTER) {
                if (c == '\n' || c == '\r') {
                    // An unterminated literal makes subsequent context unreliable.
                    break;
                }
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if ((state == State.STRING && c == '"') || (state == State.CHARACTER && c == '\'')) state = State.CODE;
                continue;
            }
            if (c == '/' && next == '/') { state = State.LINE_COMMENT; i++; continue; }
            if (c == '/' && next == '*') { state = State.BLOCK_COMMENT; i++; continue; }
            if (c == '"') { state = State.STRING; continue; }
            if (c == '\'') { state = State.CHARACTER; continue; }
            int index = FULL.indexOf(c);
            if (index >= 0) {
                String replacement = String.valueOf(ASCII.charAt(index));
                result.add(new Diagnostic("JAVA_FULLWIDTH_PUNCTUATION",
                        "代码中的全角符号「" + c + "」可能应为半角「" + replacement + "」。字符串和注释中的中文标点不作修改。",
                        line, new TextFix(source, i, 1, replacement)));
            }
        }
        return Collections.unmodifiableList(result);
    }
}
