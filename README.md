# SubtitlePad v2.0

Android external subtitle display app for SMI/SRT files.

## v2.0 changes
- SMI multi-track support: parses `<P Class=KRCC>`, `<P Class=COMM1>`, `<P Class=COMM2>`, etc. as separate selectable subtitle tracks.
- Automatically prefers `KRCC` when present.
- `자막 선택` button lets you switch tracks at any time.
- Empty SMI cues such as `&nbsp;` are preserved and clear the subtitle at that SYNC time.
- SMI entities `&nbsp;`, `&#160;`, `&#xA0;` and common HTML entities are handled.
- Font size: 18–120sp, 4sp steps.
- Adjustable line spacing.
- Controls auto-hide during playback and reappear with an upward swipe/tap/long press.
- Orientation changes retain the current playback state because the activity handles configuration changes.

## GitHub Actions
The included `.github/workflows/build.yml` builds `app-debug.apk` with Gradle 8.13 and uploads it as an Actions artifact.

Upload the contents of this folder to the repository root, then run **Actions → Build SubtitlePad APK**.
