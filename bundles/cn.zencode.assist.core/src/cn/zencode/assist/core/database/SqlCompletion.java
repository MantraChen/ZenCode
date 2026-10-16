package cn.zencode.assist.core.database;

import java.util.*;

/** Conservative SQL-context candidates from one explicitly loaded metadata snapshot. */
public final class SqlCompletion {
    public static final class Context {
        public final int start;
        public final String prefix, sql;
        Context(int start, String prefix, String sql) { this.start = start; this.prefix = prefix; this.sql = sql; }
    }
    public static Context context(String source, int offset, String extension) {
        if (offset < 0 || offset > source.length()) return null;
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        int begin = 0;
        if (!"sql".equals(ext)) {
            if (!"java".equals(ext) && !"jsp".equals(ext) && !"jspx".equals(ext)) return null;
            // Only an open Java-style quoted string; comments and closed strings never qualify.
            int quote = openString(source, offset);
            if (quote < 0) return null;
            begin = quote + 1;
            String fragment = source.substring(begin, offset).trim();
            if (!fragment.matches("(?is)^(SELECT|INSERT|UPDATE|DELETE|WITH)\\b.*")) return null;
        }
        String sql = source.substring(begin, offset);
        // Do not offer identifier completions inside SQL quoted values or comments.
        boolean single = false, line = false, block = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i), n = i+1 < sql.length() ? sql.charAt(i+1) : 0;
            if (line) { if (c == '\n') line = false; continue; }
            if (block) { if (c == '*' && n == '/') { block = false; i++; } continue; }
            if (single) { if (c == '\'') { if (n == '\'') i++; else single = false; } continue; }
            if (c == '\'') single = true;
            else if (c == '-' && n == '-') { line = true; i++; }
            else if (c == '/' && n == '*') { block = true; i++; }
        }
        if (single || line || block) return null;
        int start = offset;
        while (start > begin && (Character.isJavaIdentifierPart(source.charAt(start-1)) || source.charAt(start-1) == '.')) start--;
        return new Context(start, source.substring(start, offset), sql);
    }
    private static int openString(String source, int offset) {
        int quote = -1; boolean line = false, block = false, character = false;
        for (int i=0; i<offset; i++) {
            char c=source.charAt(i), n=i+1<offset?source.charAt(i+1):0;
            if (line) { if(c=='\n') line=false; continue; }
            if (block) { if(c=='*'&&n=='/') {block=false;i++;} continue; }
            if (quote>=0) { if(c=='\\') i++; else if(c=='"') quote=-1; continue; }
            if (character) { if(c=='\\') i++; else if(c=='\'') character=false; continue; }
            if(c=='/'&&n=='/') {line=true;i++;} else if(c=='/'&&n=='*') {block=true;i++;}
            else if(c=='\'') character=true; else if(c=='"') quote=i;
        }
        return quote;
    }
    public static List<String> candidates(List<DatabaseCatalog.Entry> snapshot, Context context) {
        if(context==null) return Collections.emptyList();
        Set<String> result=new TreeSet<String>();
        String prefix=context.prefix.toLowerCase(Locale.ROOT);
        boolean tablePosition=context.sql.substring(0,context.sql.length()-context.prefix.length()).matches("(?is).*\\b(FROM|JOIN|UPDATE|INTO)\\s+$");
        for(DatabaseCatalog.Entry entry:snapshot) {
            if(tablePosition && !entry.column.isEmpty()) continue;
            String value=entry.candidate();
            if(value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value);
        }
        return new ArrayList<String>(result);
    }
    private SqlCompletion() { }
}
