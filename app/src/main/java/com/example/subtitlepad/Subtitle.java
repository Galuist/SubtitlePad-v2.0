package com.example.subtitlepad;

public final class Subtitle {
    public final long startMs;
    public final long endMs;
    public final String text;

    public Subtitle(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text == null ? "" : text;
    }
}
