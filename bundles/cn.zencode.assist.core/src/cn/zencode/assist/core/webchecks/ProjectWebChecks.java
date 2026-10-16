package cn.zencode.assist.core.webchecks;

import java.util.*;
import java.util.regex.*;

/** Conservative checks over caller-supplied bounded, project-relative snapshots. No disk/network access. */
public final class ProjectWebChecks {
    private ProjectWebChecks() { }
    public static final class Finding {
        public final String path, message;
        public final int offset;
        Finding(String path, int offset, String message) { this.path=path; this.offset=offset; this.message=message; }
        @Override public String toString() { return path + ":" + offset + " " + message; }
    }
    private static final Pattern TAG = Pattern.compile("(?is)<([a-z][a-z0-9:-]*)\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_:][\\w:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    public static List<Finding> check(String path, Map<String,String> files) {
        return check(path,files,Collections.<String,Set<String>>emptyMap());
    }
    /** parameterCandidates contains caller-selected JSP parameter-name candidates; associations are suggestions only. */
    public static List<Finding> check(String path, Map<String,String> files, Map<String,Set<String>> parameterCandidates) {
        List<Finding> out=new ArrayList<Finding>();
        String source=files.get(path);
        if(source==null || source.length()>WebChecks.MAX_LENGTH || !path.matches("(?i).*\\.(html?|jsp)")) return out;
        String markup=mask(source, "(?s)<!--.*?(?:-->|$)|<%--.*?(?:--%>|$)|<%.*?(?:%>|$)|(?is:<(script|style)\\b[^>]*>.*?(?:</\\1\\s*>|$))");
        Matcher tags=TAG.matcher(markup);
        while(tags.find()) {
            String tag=tags.group(1).toLowerCase(Locale.ROOT);
            Matcher attrs=ATTR.matcher(tags.group(2));
            while(attrs.find()) {
                String key=attrs.group(1).toLowerCase(Locale.ROOT);
                String value=attrs.group(2)!=null?attrs.group(2):attrs.group(3);
                boolean asset=(key.equals("src") && Arrays.asList("img","script","iframe","source","audio","video").contains(tag)) || (key.equals("href") && tag.equals("link"));
                if(!asset) continue;
                String target=resolve(path,value);
                if(target!=null && !files.containsKey(target)) out.add(new Finding(path,tags.start()+1,"静态资源未在扫描范围找到："+value+"；请核对路径及大小写（动态路由不在检查范围）。"));
            }
        }
        addCssHints(path,markup,files,out);
        // Only explicit form action to a JSP snapshot. Parameters can also arrive from other clients, so suggestions only.
        Matcher forms=Pattern.compile("(?is)<form\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>(.*?)</form\\s*>").matcher(markup);
        while(forms.find()) {
            String action=attribute(forms.group(1),"action");
            String target=resolve(path,action);
            if(target==null && action!=null && action.startsWith("/") && !action.contains("?") && !action.contains("${")) {
                String selected=null; boolean ambiguous=false;
                for(Map.Entry<String,String> descriptor:files.entrySet()) if(descriptor.getKey().endsWith("/WEB-INF/web.xml")) {
                    String candidate=ServletRoutes.exactRoutes(descriptor.getValue(),files).get(action);
                    if(candidate!=null) {if(selected!=null)ambiguous=true;selected=candidate;}
                }
                if(!ambiguous) target=selected;
            }
            if(target==null || !files.containsKey(target)) continue;
            Set<String> names=new HashSet<String>();
            Matcher controls=TAG.matcher(forms.group(2));
            while(controls.find()) if(Arrays.asList("input","select","textarea","button").contains(controls.group(1).toLowerCase(Locale.ROOT))) {
                String name=attribute(controls.group(2),"name"); if(name!=null) names.add(name);
            }
            // Parameter association is explicitly selected by the caller and never proves runtime linkage.
            Set<String> parameters=parameterCandidates.get(target);
            if(parameters!=null) for(String parameter:parameters) if(!names.contains(parameter))
                out.add(new Finding(path,forms.start(),"表单提交到 "+target+"，但未发现控件 name=\""+parameter+"\"；目标存在同名 getParameter 字符串参数。仅为人工选择的关联建议，未证明接收者类型及运行关系，参数也可能由脚本或其他请求提供。"));
        }
        return out;
    }
    private static void addCssHints(String path,String markup,Map<String,String> files,List<Finding> out) {
        Set<String> classes=new TreeSet<String>(), ids=new TreeSet<String>();
        Matcher links=TAG.matcher(markup);
        boolean linked=false;
        while(links.find()) {
            if(!"link".equalsIgnoreCase(links.group(1)) || !"stylesheet".equalsIgnoreCase(attribute(links.group(2),"rel"))) continue;
            String target=resolve(path,attribute(links.group(2),"href"));
            if(target==null || !target.endsWith(".css") || !files.containsKey(target)) continue;
            String css=files.get(target); if(css.length()>WebChecks.MAX_LENGTH) continue;
            linked=true;
            // Only top-level simple selectors with literal identifiers; comments, strings, at-rules excluded.
            css=mask(css,"(?s)/\\*.*?(?:\\*/|$)|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
            Matcher selectors=Pattern.compile("(?:^|})\\s*([^{}@]+)\\{").matcher(css);
            while(selectors.find()) for(String selector:selectors.group(1).split(",")) {
                String simple=selector.trim();
                if(simple.matches("[.#][A-Za-z_][A-Za-z0-9_-]*"))
                    (simple.charAt(0)=='.'?classes:ids).add(simple.substring(1));
            }
        }
        if(!linked) return;
        Matcher tags=TAG.matcher(markup);
        while(tags.find()) for(String key:Arrays.asList("class","id")) {
            String value=attribute(tags.group(2),key);
            if(value==null || !value.matches("[A-Za-z0-9_ -]+")) continue;
            Set<String> declarations=key.equals("class")?classes:ids;
            for(String token:value.trim().split("\\s+")) if(!token.isEmpty()&&!declarations.contains(token)&&!declarations.isEmpty()) {
                List<String> candidates=new ArrayList<String>();
                for(String known:declarations) if(distance(token,known)==1) candidates.add(known);
                if(!candidates.isEmpty()) out.add(new Finding(path,tags.start(),"CSS 关联建议："+key+"=\""+token+"\" 未匹配已链接本地样式的简单声明；候选 "+candidates+"。可能由动态、内联或外部样式提供，不视为错误，不自动修改。"));
            }
        }
    }
    private static int distance(String a,String b) {
        if(Math.abs(a.length()-b.length())>1)return 2;
        int[] row=new int[b.length()+1]; for(int j=0;j<row.length;j++)row[j]=j;
        for(int i=1;i<=a.length();i++){int prev=row[0];row[0]=i;for(int j=1;j<=b.length();j++){int old=row[j];row[j]=Math.min(Math.min(row[j]+1,row[j-1]+1),prev+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=old;}}
        return row[b.length()];
    }
    private static String attribute(String text,String key) { Matcher m=ATTR.matcher(text); while(m.find()) if(key.equalsIgnoreCase(m.group(1))) return m.group(2)!=null?m.group(2):m.group(3); return null; }
    public static String resolve(String path,String value) {
        if(value==null || value.isEmpty() || value.startsWith("/") || value.startsWith("#") || value.matches(".*[:${}%<>?&\\\\].*") || value.contains("#{")) return null;
        int hash=value.indexOf('#'); if(hash>=0)value=value.substring(0,hash);
        List<String> parts=new ArrayList<String>(Arrays.asList(path.split("/"))); parts.remove(parts.size()-1);
        for(String part:value.split("/")) { if(part.equals("..")) {if(parts.isEmpty())return null; parts.remove(parts.size()-1);} else if(!part.equals(".")&&!part.isEmpty())parts.add(part); }
        return String.join("/",parts);
    }
    private static String mask(String s,String regex) { char[] chars=s.toCharArray(); Matcher m=Pattern.compile(regex).matcher(s); while(m.find()) for(int i=m.start();i<m.end();i++) if(chars[i]!='\n'&&chars[i]!='\r') chars[i]=' '; return new String(chars); }
}
