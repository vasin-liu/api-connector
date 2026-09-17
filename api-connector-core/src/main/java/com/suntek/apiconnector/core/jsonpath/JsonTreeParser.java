/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.jsonpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for Condition JSONPath evaluation. Invalid JSON is not an AST error.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
final class JsonTreeParser {

    private final String text;
    private int index;

    private JsonTreeParser(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        JsonTreeParser parser = new JsonTreeParser(text);
        Object value = parser.value();
        parser.skipWs();
        if (parser.index != parser.text.length()) {
            throw new JsonParseException("trailing content at " + parser.index);
        }
        return value;
    }

    private Object value() {
        skipWs();
        if (index >= text.length()) {
            throw new JsonParseException("unexpected end");
        }
        char c = text.charAt(index);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> number();
            default -> throw new JsonParseException("unexpected '" + c + "' at " + index);
        };
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek('}')) {
            index++;
            return map;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            expect(':');
            Object val = value();
            map.put(key, val);
            skipWs();
            if (peek('}')) {
                index++;
                return map;
            }
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWs();
        if (peek(']')) {
            index++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWs();
            if (peek(']')) {
                index++;
                return list;
            }
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (index >= text.length()) {
                    throw new JsonParseException("unterminated escape");
                }
                char e = text.charAt(index++);
                sb.append(switch (e) {
                    case '"', '\\', '/' -> e;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> unicode();
                    default -> throw new JsonParseException("bad escape \\" + e);
                });
            } else if (c < 0x20) {
                throw new JsonParseException("unescaped control in string");
            } else {
                sb.append(c);
            }
        }
        throw new JsonParseException("unterminated string");
    }

    private char unicode() {
        if (index + 4 > text.length()) {
            throw new JsonParseException("bad unicode escape");
        }
        int code = 0;
        for (int i = 0; i < 4; i++) {
            code = (code << 4) | hex(text.charAt(index++));
        }
        return (char) code;
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new JsonParseException("bad hex " + c);
    }

    private Object literal(String expected, Object value) {
        if (!text.startsWith(expected, index)) {
            throw new JsonParseException("expected " + expected + " at " + index);
        }
        index += expected.length();
        return value;
    }

    private Number number() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        if (index >= text.length()) {
            throw new JsonParseException("bad number");
        }
        if (text.charAt(index) == '0') {
            index++;
        } else if (text.charAt(index) >= '1' && text.charAt(index) <= '9') {
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
        } else {
            throw new JsonParseException("bad number at " + index);
        }
        boolean fraction = false;
        if (peek('.')) {
            fraction = true;
            index++;
            int digits = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (index == digits) {
                throw new JsonParseException("bad fraction");
            }
        }
        if (peek('e') || peek('E')) {
            fraction = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            int digits = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (index == digits) {
                throw new JsonParseException("bad exponent");
            }
        }
        String raw = text.substring(start, index);
        if (!fraction) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ex) {
                return new java.math.BigDecimal(raw);
            }
        }
        return Double.parseDouble(raw);
    }

    private void skipWs() {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                index++;
            } else {
                return;
            }
        }
    }

    private boolean peek(char c) {
        return index < text.length() && text.charAt(index) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw new JsonParseException("expected '" + c + "' at " + index);
        }
        index++;
    }

    static final class JsonParseException extends RuntimeException {
        JsonParseException(String message) {
            super(message);
        }
    }
}
