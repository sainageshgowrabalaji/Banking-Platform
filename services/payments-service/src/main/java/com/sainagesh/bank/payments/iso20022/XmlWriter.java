package com.sainagesh.bank.payments.iso20022;

import java.util.ArrayDeque;
import java.util.Deque;

/** Writes indented XML, one element at a time. Text and attribute values are always escaped. */
final class XmlWriter {

    private final StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    private final Deque<String> open = new ArrayDeque<>();

    /** Opens an element that will hold other elements. Attributes are given as name, value pairs. */
    XmlWriter open(String name, String... attributes) {
        indent();
        out.append('<').append(name);
        appendAttributes(attributes);
        out.append(">\n");
        open.push(name);
        return this;
    }

    /** Writes an element with text. An element whose value is null or blank is left out. */
    XmlWriter text(String name, String value, String... attributes) {
        if (value == null || value.isBlank()) {
            return this;
        }
        indent();
        out.append('<').append(name);
        appendAttributes(attributes);
        out.append('>').append(escape(value)).append("</").append(name).append(">\n");
        return this;
    }

    XmlWriter close() {
        String name = open.pop();
        indent();
        out.append("</").append(name).append(">\n");
        return this;
    }

    String finish() {
        while (!open.isEmpty()) {
            close();
        }
        return out.toString();
    }

    private void appendAttributes(String... attributes) {
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            out.append(' ').append(attributes[i]).append("=\"").append(escape(attributes[i + 1])).append('"');
        }
    }

    private void indent() {
        out.append("  ".repeat(open.size()));
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                // XML allows tab, line feed and carriage return but no other control character, and
                // it has no place for half of a surrogate pair or for U+FFFE and U+FFFF. Any of those
                // would make a file no parser accepts, so they are left out.
                default -> {
                    boolean pair = Character.isHighSurrogate(c)
                            && i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1));
                    if (pair) {
                        escaped.append(c).append(value.charAt(++i));
                    } else if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xFFFD && !Character.isSurrogate(c))) {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
