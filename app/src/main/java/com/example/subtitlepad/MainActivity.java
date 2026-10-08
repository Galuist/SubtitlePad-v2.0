package com.example.subtitlepad;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_SUBTITLE = 1001;
    private static final long HIDE_DELAY_MS = 3500;

    private TextView subtitleView, timeView, fileView;
    private SeekBar seekBar;
    private Button playButton, trackButton;
    private LinearLayout controlsContainer;
    private ArrayList<SubtitleTrack> tracks = new ArrayList<>();
    private int selectedTrack = 0;
    private long positionMs = 0, syncMs = 0;
    private boolean playing = false, systemBarsHidden = false;
    private float textSizeSp = 34f;
    private float lineSpacingExtraSp = 2f;
    private long lastTick;
    private float touchDownY;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideRunnable = () -> {
        if (playing) setControlsVisible(false);
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            if (playing) {
                positionMs += Math.max(0, now - lastTick);
                updateUi();
            }
            lastTick = now;
            handler.postDelayed(this, 50);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();
        lastTick = SystemClock.elapsedRealtime();
        handler.post(ticker);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(0, 0, 0, 0);

        fileView = label("자막 파일을 선택하세요", 16);
        fileView.setGravity(Gravity.CENTER);
        root.addView(fileView, new LinearLayout.LayoutParams(-1, 42));

        FrameLayout subtitleBox = new FrameLayout(this);
        subtitleBox.setBackgroundColor(Color.BLACK);
        subtitleView = new TextView(this);
        subtitleView.setTextColor(Color.WHITE);
        subtitleView.setTextSize(textSizeSp);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setShadowLayer(5, 2, 2, Color.BLACK);
        subtitleView.setPadding(0, 0, 0, 0);
        subtitleView.setLineSpacing(dp(lineSpacingExtraSp), 1f);
        subtitleBox.addView(subtitleView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(subtitleBox, new LinearLayout.LayoutParams(-1, 0, 1));

        timeView = label("00:00:00 / 00:00:00", 14);
        timeView.setGravity(Gravity.CENTER);
        root.addView(timeView, new LinearLayout.LayoutParams(-1, 34));

        seekBar = new SeekBar(this);
        seekBar.setMax(100000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && !currentSubtitles().isEmpty()) {
                    long duration = currentSubtitles().get(currentSubtitles().size()-1).endMs;
                    positionMs = duration * p / 100000L;
                    updateUi(); showControlsTemporarily();
                }
            }
            public void onStartTrackingTouch(SeekBar s) { showControlsTemporarily(); }
            public void onStopTrackingTouch(SeekBar s) { showControlsTemporarily(); }
        });
        root.addView(seekBar, new LinearLayout.LayoutParams(-1, 42));

        controlsContainer = new LinearLayout(this);
        controlsContainer.setOrientation(LinearLayout.VERTICAL);
        controlsContainer.setBackgroundColor(Color.BLACK);

        LinearLayout row1 = new LinearLayout(this); row1.setGravity(Gravity.CENTER);
        addButton(row1, "자막 열기", v -> pickSubtitle());
        trackButton = addButton(row1, "자막 선택", v -> chooseTrack());
        addButton(row1, "−10초", v -> jump(-10000));
        addButton(row1, "−1초", v -> jump(-1000));
        playButton = addButton(row1, "재생", v -> togglePlay());
        addButton(row1, "+1초", v -> jump(1000));
        addButton(row1, "+10초", v -> jump(10000));
        controlsContainer.addView(row1, new LinearLayout.LayoutParams(-1, 58));

        LinearLayout row2 = new LinearLayout(this); row2.setGravity(Gravity.CENTER);
        addButton(row2, "싱크 −0.1", v -> adjustSync(-100));
        addButton(row2, "싱크 +0.1", v -> adjustSync(100));
        addButton(row2, "글자 −", v -> changeTextSize(-4));
        addButton(row2, "글자 +", v -> changeTextSize(4));
        addButton(row2, "줄 간격 −", v -> changeLineSpacing(-1));
        addButton(row2, "줄 간격 +", v -> changeLineSpacing(1));
        addButton(row2, "처음", v -> { positionMs = 0; updateUi(); showControlsTemporarily(); });
        controlsContainer.addView(row2, new LinearLayout.LayoutParams(-1, 58));
        root.addView(controlsContainer, new LinearLayout.LayoutParams(-1, 116));

        TextView hint = label("싱크: 0.00초  ·  화면을 위로 쓸어올리거나 길게 누르면 컨트롤 표시", 12);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, 34));

        subtitleBox.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { touchDownY = event.getY(); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - touchDownY;
                if (dy < -50) { setControlsVisible(true); return true; }
                if (Math.abs(dy) < 20 && !controlsContainer.isShown()) { setControlsVisible(true); return true; }
            }
            return true;
        });
        subtitleBox.setOnLongClickListener(v -> { setControlsVisible(true); Toast.makeText(this, String.format(Locale.US, "싱크 %+.2f초", syncMs/1000.0), Toast.LENGTH_SHORT).show(); return true; });

        setContentView(root);
    }

    private TextView label(String text, float size) {
        TextView t = new TextView(this); t.setText(text); t.setTextColor(Color.LTGRAY); t.setTextSize(size); return t;
    }

    private Button addButton(LinearLayout row, String text, View.OnClickListener l) {
        Button b = new Button(this); b.setText(text); b.setTextSize(11); b.setOnClickListener(v -> { l.onClick(v); showControlsTemporarily(); });
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1)); return b;
    }

    private void pickSubtitle() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, PICK_SUBTITLE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SUBTITLE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            byte[] bytes;
            try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); bytes = out.toByteArray();
            }
            String raw = Encoding.decode(bytes), name = getFileName(uri);
            String ext = ""; int dot = name.lastIndexOf('.'); if (dot >= 0) ext = name.substring(dot + 1).toLowerCase(Locale.US);
            if (!ext.equals("smi") && !ext.equals("srt")) { Toast.makeText(this, "SMI 또는 SRT 파일을 선택하세요.\n선택된 파일: " + name, Toast.LENGTH_LONG).show(); return; }
            List<SubtitleTrack> parsed = SubtitleParser.parseTracks(raw, ext);
            if (parsed.isEmpty()) { Toast.makeText(this, "자막을 찾지 못했습니다. 파일 형식이나 인코딩을 확인하세요.\n파일: " + name, Toast.LENGTH_LONG).show(); subtitleView.setText(""); return; }
            tracks.clear(); tracks.addAll(parsed);
            selectedTrack = chooseDefaultTrack();
            positionMs = 0; syncMs = 0; playing = false; playButton.setText("재생");
            fileView.setText(name + "  ·  " + tracks.get(selectedTrack).name + "  ·  " + currentSubtitles().size() + "개 자막");
            trackButton.setText("자막: " + tracks.get(selectedTrack).name);
            updateUi(); setControlsVisible(true);
            if (tracks.size() > 1) chooseTrack();
        } catch (Exception e) { Toast.makeText(this, "자막을 읽을 수 없습니다: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private int chooseDefaultTrack() {
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).name.equalsIgnoreCase("KRCC")) return i;
        return 0;
    }

    private void chooseTrack() {
        if (tracks.isEmpty()) { Toast.makeText(this, "먼저 자막 파일을 열어주세요.", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) names[i] = tracks.get(i).name + "  (" + tracks.get(i).subtitles.size() + "개)";
        new AlertDialog.Builder(this).setTitle("자막 선택")
            .setSingleChoiceItems(names, selectedTrack, (d, which) -> { selectedTrack = which; d.dismiss(); refreshTrackLabel(); updateUi(); })
            .setNegativeButton("취소", null).show();
    }

    private void refreshTrackLabel() {
        if (tracks.isEmpty()) return;
        trackButton.setText("자막: " + tracks.get(selectedTrack).name);
        String current = fileView.getText().toString();
        int sep = current.indexOf("  ·  ");
        String file = sep >= 0 ? current.substring(0, sep) : current;
        fileView.setText(file + "  ·  " + tracks.get(selectedTrack).name + "  ·  " + currentSubtitles().size() + "개 자막");
    }

    private String getFileName(Uri uri) {
        String result = "subtitle";
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) { int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) result = c.getString(idx); }
        } catch (Exception ignored) {}
        return result;
    }

    private ArrayList<Subtitle> currentSubtitles() { return tracks.isEmpty() ? new ArrayList<>() : tracks.get(selectedTrack).subtitles; }

    private void togglePlay() {
        if (tracks.isEmpty()) { Toast.makeText(this, "먼저 SMI 또는 SRT 파일을 열어주세요.", Toast.LENGTH_SHORT).show(); return; }
        playing = !playing; lastTick = SystemClock.elapsedRealtime(); playButton.setText(playing ? "일시정지" : "재생");
        if (playing) showControlsTemporarily(); else setControlsVisible(true);
    }

    private void jump(long delta) { positionMs = Math.max(0, positionMs + delta); updateUi(); showControlsTemporarily(); }
    private void adjustSync(long delta) { syncMs += delta; updateUi(); Toast.makeText(this, String.format(Locale.US, "싱크 %+.1f초", syncMs/1000.0), Toast.LENGTH_SHORT).show(); }
    private void changeTextSize(float delta) { textSizeSp = Math.max(18, Math.min(120, textSizeSp + delta)); subtitleView.setTextSize(textSizeSp); }
    private void changeLineSpacing(float delta) { lineSpacingExtraSp = Math.max(0, Math.min(20, lineSpacingExtraSp + delta)); subtitleView.setLineSpacing(dp(lineSpacingExtraSp), 1f); }

    private void updateUi() {
        ArrayList<Subtitle> list = currentSubtitles();
        long effective = positionMs + syncMs; Subtitle current = null;
        for (Subtitle s : list) { if (effective >= s.startMs && effective < s.endMs) { current = s; break; } if (s.startMs > effective) break; }
        subtitleView.setText(current == null ? "" : current.text);
        long duration = list.isEmpty() ? 0 : list.get(list.size()-1).endMs;
        timeView.setText(format(positionMs) + " / " + format(duration) + String.format(Locale.US, "    싱크 %+.2fs", syncMs/1000.0));
        if (duration > 0) seekBar.setProgress((int)Math.min(100000L, positionMs * 100000L / duration));
    }

    private void setControlsVisible(boolean visible) {
        controlsContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        timeView.setVisibility(visible ? View.VISIBLE : View.GONE);
        seekBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        fileView.setVisibility(visible ? View.VISIBLE : View.GONE);
        systemBarsHidden = !visible;
        if (visible) showSystemBars(); else hideSystemBars();
        handler.removeCallbacks(hideRunnable);
        if (visible && playing) handler.postDelayed(hideRunnable, HIDE_DELAY_MS);
    }
    private void showControlsTemporarily() { setControlsVisible(true); }
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private void showSystemBars() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String format(long ms) { long sec = Math.max(0, ms)/1000, h=sec/3600, m=(sec%3600)/60, s=sec%60; return String.format(Locale.US, "%02d:%02d:%02d", h,m,s); }

    @Override protected void onDestroy() { handler.removeCallbacks(ticker); handler.removeCallbacks(hideRunnable); super.onDestroy(); }
}
