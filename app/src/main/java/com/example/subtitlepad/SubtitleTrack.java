package com.example.subtitlepad;

import java.util.ArrayList;
import java.util.List;

public final class SubtitleTrack {
    public final String name;
    public final ArrayList<Subtitle> subtitles;

    public SubtitleTrack(String name, List<Subtitle> subtitles) {
        this.name = name;
        this.subtitles = new ArrayList<>(subtitles);
    }
}
