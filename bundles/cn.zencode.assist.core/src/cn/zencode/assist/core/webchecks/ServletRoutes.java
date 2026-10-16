package cn.zencode.assist.core.webchecks;

import java.io.StringReader;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Exact deployment descriptor routes only. XML external access is disabled. */
public final class ServletRoutes {
    private ServletRoutes() { }
    public static Map<String,String> exactRoutes(String xml, Map<String,String> files) {
        Map<String,String> result=new LinkedHashMap<String,String>();
        if(xml==null || xml.length()>WebChecks.MAX_LENGTH) return result;
        try {
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {throw e;}
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {throw e;}
            });
            Document doc=builder.parse(new InputSource(new StringReader(xml)));
            Map<String,String> names=new HashMap<String,String>();
            NodeList servlets=doc.getElementsByTagName("servlet");
            for(int i=0;i<servlets.getLength();i++) {
                Element e=(Element)servlets.item(i); String name=child(e,"servlet-name"), type=child(e,"servlet-class");
                if(name==null || type==null || !type.matches("[A-Za-z_$][A-Za-z0-9_$.]*"))continue;
                String suffix="/"+type.replace('.','/')+".java", match=null; boolean ambiguous=false;
                for(String path:files.keySet()) if(("/"+path).endsWith(suffix)) { if(match!=null)ambiguous=true; match=path; }
                if(match!=null&&!ambiguous) names.put(name,match);
            }
            Set<String> duplicates=new HashSet<String>();
            NodeList mappings=doc.getElementsByTagName("servlet-mapping");
            for(int i=0;i<mappings.getLength();i++) {
                Element e=(Element)mappings.item(i); String target=names.get(child(e,"servlet-name"));
                NodeList patterns=e.getElementsByTagName("url-pattern");
                for(int j=0;j<patterns.getLength();j++) {
                    String route=patterns.item(j).getTextContent().trim();
                    if(target==null || !route.startsWith("/") || route.contains("*") || route.equals("/"))continue;
                    if(result.containsKey(route)) duplicates.add(route); else result.put(route,target);
                }
            }
            for(String duplicate:duplicates)result.remove(duplicate);
        } catch(Exception invalid) { return Collections.emptyMap(); }
        return Collections.unmodifiableMap(result);
    }
    private static String child(Element parent,String name) {
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element && name.equals(n.getNodeName()))return n.getTextContent().trim();
        return null;
    }
}
