package com.guandan.solo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 编解码：只支持对象/数组/字符串/数字/布尔/null，够协议用 */
public class Json {

    public static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else b.append(c);
        }
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    public static String str(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return "\"" + esc((String) o) + "\"";
        if (o instanceof Map) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append("\"").append(esc(e.getKey())).append("\":").append(str(e.getValue()));
            }
            return b.append('}').toString();
        }
        if (o instanceof List) {
            StringBuilder b = new StringBuilder("[");
            List<Object> l = (List<Object>) o;
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) b.append(',');
                b.append(str(l.get(i)));
            }
            return b.append(']').toString();
        }
        if (o instanceof Boolean) return String.valueOf(((Boolean) o).booleanValue());
        if (o instanceof Double || o instanceof Float) {
            double dv = ((Number) o).doubleValue();
            if (dv == Math.rint(dv) && !Double.isInfinite(dv)) return String.valueOf((long) dv);
            return String.valueOf(dv);
        }
        return String.valueOf(o);
    }

    public static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    /** 解析 */
    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        return p.value();
    }

    private static class P {
        String s;
        int i = 0;
        P(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        Object value() {
            ws();
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (i < s.length()) {
                ws();
                String k = string();
                ws();
                if (i < s.length() && s.charAt(i) == ':') i++;
                Object v = value();
                m.put(k, v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') { i++; break; }
                break;
            }
            return m;
        }

        List<Object> array() {
            List<Object> l = new ArrayList<Object>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (i < s.length()) {
                l.add(value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') { i++; break; }
                break;
            }
            return l;
        }

        String string() {
            StringBuilder b = new StringBuilder();
            if (i < s.length() && s.charAt(i) == '"') i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    if (e == 'n') b.append('\n');
                    else if (e == 'r') b.append('\r');
                    else if (e == 't') b.append('\t');
                    else b.append(e);
                } else if (c == '"') break;
                else b.append(c);
            }
            return b.toString();
        }

        Double number() {
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    b.append(c);
                    i++;
                } else break;
            }
            try { return Double.valueOf(b.toString()); } catch (Exception ex) { return Double.valueOf(0); }
        }
    }

    public static int i(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
    public static String s(Object o) { return o == null ? "" : String.valueOf(o); }
    public static boolean b(Object o) { return o instanceof Boolean && ((Boolean) o).booleanValue(); }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> m(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>(); }

    @SuppressWarnings("unchecked")
    public static List<Object> l(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<Object>(); }
}
