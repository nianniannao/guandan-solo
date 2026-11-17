package com.guandan.solo;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/** 语音播报：出牌、不要、轮到你出牌，可随时关闭。多引擎/多语言兜底。 */
public class Voice {

    private final Context ctx;
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean langOk = false;
    private boolean enabled = true;
    private boolean scanned = false;
    private boolean errorOnce = false;
    private final ArrayDeque<String> pending = new ArrayDeque<String>();
    private ToneGenerator tone;

    private final TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
        public void onInit(int status) { handleInit(status); }
    };

    public Voice(Context c) {
        ctx = c.getApplicationContext();
        open(null);
    }

    private void open(String engine) {
        try {
            if (tts != null) { tts.shutdown(); tts = null; }
            tts = (engine == null) ? new TextToSpeech(ctx, listener) : new TextToSpeech(ctx, listener, engine);
        } catch (Throwable e) {
            tts = null;
        }
    }

    private void handleInit(int status) {
        if (tts == null) return;
        if (status != TextToSpeech.SUCCESS) {
            if (!scanned) { scanned = true; if (tryNextEngine(null)) return; }
            ready = false;
            return;
        }
        configure();
        if (!langOk && !scanned) {
            scanned = true;
            if (tryNextEngine(currentEngine())) return;
        }
        ready = true;
        flush();
    }

    private void configure() {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
            int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(Locale.CHINESE);
            }
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(Locale.getDefault());
            }
            langOk = !(r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED);
            tts.setSpeechRate(1.05f);
            tts.setPitch(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) { }
                public void onDone(String id) { }
                public void onError(String id) {
                    if (errorOnce) return;
                    errorOnce = true;
                    if (!scanned) { scanned = true; if (tryNextEngine(currentEngine())) return; }
                }
            });
        } catch (Throwable e) {
        }
    }

    private String currentEngine() {
        try { return tts.getDefaultEngine(); } catch (Throwable e) { return null; }
    }

    /** 换一个已安装的引擎再试一次，成功返回 true */
    private boolean tryNextEngine(String exclude) {
        try {
            List<TextToSpeech.EngineInfo> list = tts != null ? tts.getEngines() : null;
            if (list == null) return false;
            for (int i = 0; i < list.size(); i++) {
                String n = list.get(i).name;
                if (n == null || n.equals(exclude)) continue;
                open(n);
                return true;
            }
        } catch (Throwable e) { }
        return false;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on && tts != null) {
            try { tts.stop(); } catch (Throwable e) { }
        }
        if (!on) pending.clear();
    }

    public boolean isEnabled() { return enabled; }

    /** 是否拿到可用语音引擎 */
    public boolean isReady() { return ready || tts != null; }

    /** 引擎是否支持中文 */
    public boolean hasChinese() { return langOk; }

    /** 轮到出牌时的轻提示音，不依赖 TTS */
    public void beep() {
        if (!enabled) return;
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 75);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160);
        } catch (Throwable e) { }
    }

    public void speak(String text) {
        if (!enabled || text == null || text.length() == 0) return;
        if (!ready) {
            if (pending.size() < 3) pending.add(text);
            return;
        }
        speakNow(text);
    }

    private void flush() {
        while (!pending.isEmpty()) {
            String s = pending.poll();
            if (s != null) speakNow(s);
        }
    }

    private void speakNow(String text) {
        if (tts == null) return;
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gd" + System.nanoTime());
        } catch (Throwable e) { }
    }

    public void shutdown() {
        if (tone != null) {
            try { tone.release(); } catch (Throwable e) { }
            tone = null;
        }
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable e) { }
            tts = null;
        }
    }
}
