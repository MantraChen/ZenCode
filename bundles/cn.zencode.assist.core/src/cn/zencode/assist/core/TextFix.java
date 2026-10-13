package cn.zencode.assist.core;

/** Immutable edit bound to the entire source snapshot. Coordinates are UTF-16 offsets. */
public final class TextFix {
    private final String snapshot;
    private final int offset;
    private final int length;
    private final String replacement;

    public TextFix(String snapshot, int offset, int length, String replacement) {
        if (snapshot == null || replacement == null || offset < 0 || length < 0
                || offset > snapshot.length() || length > snapshot.length() - offset) {
            throw new IllegalArgumentException("Invalid edit range");
        }
        this.snapshot = snapshot;
        this.offset = offset;
        this.length = length;
        this.replacement = replacement;
    }

    public boolean isCurrent(String current) { return snapshot.equals(current); }
    public int getOffset() { return offset; }
    public int getLength() { return length; }
    public String getReplacement() { return replacement; }
    public String apply(String current) {
        if (!isCurrent(current)) throw new IllegalStateException("源码已经变化，请重新检查后再应用修复。");
        return current.substring(0, offset) + replacement + current.substring(offset + length);
    }
}
