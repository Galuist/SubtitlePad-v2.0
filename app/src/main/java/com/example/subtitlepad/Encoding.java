package com.example.subtitlepad;

import java.nio.charset.*;

public final class Encoding {
    public static String decode(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xff) == 0xef && (data[1] & 0xff) == 0xbb && (data[2] & 0xff) == 0xbf)
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        if (data.length >= 2 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xfe)
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        if (data.length >= 2 && (data[0] & 0xff) == 0xfe && (data[1] & 0xff) == 0xff)
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        String utf8 = new String(data, StandardCharsets.UTF_8);
        if (count(utf8, '\uFFFD') == 0) return utf8;
        try { return new String(data, Charset.forName("MS949")); }
        catch (Exception ignored) { return utf8; }
    }
    private static int count(String s, char c) {
        int n = 0; for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++; return n;
    }
}
