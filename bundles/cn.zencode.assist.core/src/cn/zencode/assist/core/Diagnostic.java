package cn.zencode.assist.core;

public final class Diagnostic {
    private final String code;
    private final String message;
    private final int line;
    private final TextFix fix;

    public Diagnostic(String code, String message, int line, TextFix fix) {
        this.code = code; this.message = message; this.line = line; this.fix = fix;
    }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public int getLine() { return line; }
    public TextFix getFix() { return fix; }
}
