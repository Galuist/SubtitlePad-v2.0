package com.example.subtitlepad;

import java.util.*;
import java.util.regex.*;

public final class SubtitleParser {
    public static List<SubtitleTrack> parseTracks(String raw, String extension) {
        if ("smi".equalsIgnoreCase(extension)) return parseSmiTracks(raw);
        List<Subtitle> srt = parseSrt(raw);
        ArrayList<SubtitleTrack> out = new ArrayList<>();
        if (!srt.isEmpty()) out.add(new SubtitleTrack("SRT", srt));
        return out;
    }

    public static List<Subtitle> parse(String raw, String extension) {
        List<SubtitleTrack> tracks = parseTracks(raw, extension);
        return tracks.isEmpty() ? new ArrayList<>() : tracks.get(0).subtitles;
    }

    private static List<Subtitle> parseSrt(String raw) {
        List<Subtitle> out = new ArrayList<>();
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n");
        Pattern time = Pattern.compile("(?m)(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})");
        for (String block : blocks) {
            Matcher m = time.matcher(block);
            if (!m.find()) continue;
            long start = parseTime(m.group(1));
            long end = parseTime(m.group(2));
            String text = cleanText(block.substring(m.end()));
            if (!text.isEmpty()) out.add(new Subtitle(start, end, text));
        }
        return normalize(out);
    }

    private static List<SubtitleTrack> parseSmiTracks(String raw) {
        // A normal SMI can contain several subtitle tracks inside each SYNC:
        // <SYNC Start=1000><P Class=KRCC>...</P><P Class=COMM1>...</P>
        // Every track is parsed independently. Empty P tags such as &nbsp; are
        // retained so they explicitly clear the selected track at that SYNC.
        LinkedHashMap<String, ArrayList<SmiEvent>> eventsByTrack = new LinkedHashMap<>();
        Pattern syncPattern = Pattern.compile("(?is)<sync\\b[^>]*\\bstart\\s*=\\s*['\"]?(\\d+)['\"]?[^>]*>(.*?)(?=<sync\\b|$)");
        Pattern pPattern = Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p\\s*>");
        Pattern classPattern = Pattern.compile("(?i)\\bclass\\s*=\\s*['\"]?([^\\s'\">]+)");

        Matcher syncMatcher = syncPattern.matcher(raw);
        while (syncMatcher.find()) {
            long start = Long.parseLong(syncMatcher.group(1));
            String body = syncMatcher.group(2);
            Matcher pMatcher = pPattern.matcher(body);
            boolean foundP = false;
            while (pMatcher.find()) {
                foundP = true;
                String attrs = pMatcher.group(1);
                Matcher cm = classPattern.matcher(attrs);
                String track = cm.find() ? cm.group(1).trim() : "DEFAULT";
                String text = cleanText(pMatcher.group(2));
                eventsByTrack.computeIfAbsent(track, k -> new ArrayList<>()).add(new SmiEvent(start, text));
            }
            // Some SMI files omit </P>. Support a class-bearing P tag up to the next tag/SYNC.
            if (!foundP) {
                Pattern looseP = Pattern.compile("(?is)<p\\b([^>]*)>(.*)");
                Matcher lm = looseP.matcher(body);
                if (lm.find()) {
                    Matcher cm = classPattern.matcher(lm.group(1));
                    String track = cm.find() ? cm.group(1).trim() : "DEFAULT";
                    eventsByTrack.computeIfAbsent(track, k -> new ArrayList<>()).add(new SmiEvent(start, cleanText(lm.group(2))));
                }
            }
        }

        ArrayList<SubtitleTrack> tracks = new ArrayList<>();
        for (Map.Entry<String, ArrayList<SmiEvent>> e : eventsByTrack.entrySet()) {
            ArrayList<SmiEvent> events = e.getValue();
            events.sort(Comparator.comparingLong(x -> x.startMs));
            ArrayList<Subtitle> cues = new ArrayList<>();
            for (int i = 0; i < events.size(); i++) {
                SmiEvent cur = events.get(i);
                long end = (i + 1 < events.size()) ? events.get(i + 1).startMs : cur.startMs + 5000;
                if (end <= cur.startMs) end = cur.startMs + 1;
                cues.add(new Subtitle(cur.startMs, end, cur.text));
            }
            tracks.add(new SubtitleTrack(e.getKey(), normalize(cues)));
        }

        // If there are no <P> tags at all, retain the older generic SYNC parser as a fallback.
        if (tracks.isEmpty()) {
            ArrayList<Subtitle> generic = parseGenericSmi(raw);
            if (!generic.isEmpty()) tracks.add(new SubtitleTrack("DEFAULT", generic));
        }
        return tracks;
    }

    private static ArrayList<Subtitle> parseGenericSmi(String raw) {
        List<Subtitle> out = new ArrayList<>();
        Pattern p = Pattern.compile("(?is)<sync\\s+start\\s*=\\s*['\"]?(\\d+)['\"]?[^>]*>(.*?)(?=<sync\\s+start\\s*=|$)");
        Matcher m = p.matcher(raw);
        ArrayList<Long> starts = new ArrayList<>();
        ArrayList<String> texts = new ArrayList<>();
        while (m.find()) {
            starts.add(Long.parseLong(m.group(1)));
            texts.add(cleanText(m.group(2)));
        }
        for (int i = 0; i < starts.size(); i++) {
            long end = i + 1 < starts.size() ? starts.get(i + 1) : starts.get(i) + 5000;
            out.add(new Subtitle(starts.get(i), end, texts.get(i)));
        }
        return normalize(out);
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?font\\b[^>]*>", "")
                .replaceAll("(?i)</?span\\b[^>]*>", "")
                .replaceAll("(?i)</?p\\b[^>]*>", "")
                .replaceAll("(?i)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&nbsp", " ")
                .replace("&#160;", " ")
                .replace("&#xA0;", " ")
                .replace("&#xa0;", " ")
                .replace('\u00A0', ' ')
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    private static List<Subtitle> normalize(List<Subtitle> list) {
        list.sort(Comparator.comparingLong(s -> s.startMs));
        List<Subtitle> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Subtitle s = list.get(i);
            long end = s.endMs;
            if (end <= s.startMs) end = i + 1 < list.size() ? list.get(i + 1).startMs : s.startMs + 5000;
            out.add(new Subtitle(s.startMs, end, s.text));
        }
        return out;
    }

    private static long parseTime(String s) {
        String[] a = s.replace(',', '.').split(":");
        double sec = Double.parseDouble(a[2]);
        return Math.round((Long.parseLong(a[0]) * 3600 + Long.parseLong(a[1]) * 60 + sec) * 1000.0);
    }

    private static final class SmiEvent {
        final long startMs; final String text;
        SmiEvent(long startMs, String text) { this.startMs = startMs; this.text = text; }
    }
}
