package com.guandan.solo;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 内置离线中文播报：素材随 APK 打包，不依赖系统 TTS */
public class LocalVoice {

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> pool = new HashMap<String, Integer>();
    private final Map<String, Integer> dur = new HashMap<String, Integer>();
    private SoundPool sp;
    private int loaded = 0;
    private int total = 0;
    private boolean ready = false;
    private boolean disabled = false;
    private List<String> queue = new ArrayList<String>();
    private int cursor = 0;
    private Runnable chain;

    public LocalVoice(Context c) {
        ctx = c.getApplicationContext();
        try {
            prepare();
        } catch (Throwable e) {
            disabled = true;
        }
    }

    private void prepare() throws Exception {
        AssetManager am = ctx.getAssets();
        String[] files = am.list("voice");
        if (files == null || files.length < 10) { disabled = true; return; }

        File dir = new File(ctx.getFilesDir(), "voice");
        if (!dir.exists()) dir.mkdirs();
        for (int i = 0; i < files.length; i++) {
            String n = files[i];
            if (!n.endsWith(".mp3")) continue;
            File out = new File(dir, n);
            if (!out.exists() || out.length() < 500) {
                InputStream in = am.open("voice/" + n);
                OutputStream os = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                os.close();
                in.close();
            }
        }

        InputStream in = am.open("voice/index.txt");
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) continue;
            String[] p = line.split(" ");
            if (p.length != 2) continue;
            dur.put(p[0], Integer.parseInt(p[1]));
        }
        br.close();

        sp = new SoundPool.Builder().setMaxStreams(2).build();
        sp.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                loaded++;
                if (loaded >= total && total > 0) ready = true;
            }
        });
        total = 0;
        for (String k : dur.keySet()) {
            int id = sp.load(new File(dir, k + ".mp3").getAbsolutePath(), 1);
            if (id > 0) { pool.put(k, id); total++; }
        }
        if (total == 0) disabled = true;
    }

    public boolean isReady() { return ready && !disabled; }
    public boolean isDisabled() { return disabled; }

    /** 依次播放若干片段，可打断上一条 */
    public void play(String... parts) {
        if (!ready || disabled) return;
        handler.removeCallbacksAndMessages(null);
        try { sp.stop(1); sp.stop(2); } catch (Throwable e) { }
        queue.clear();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && pool.containsKey(parts[i])) queue.add(parts[i]);
        }
        cursor = 0;
        chain = new Runnable() {
            public void run() {
                if (cursor >= queue.size()) return;
                String k = queue.get(cursor);
                Integer id = pool.get(k);
                if (id != null) sp.play(id, 1.0f, 1.0f, 1, 0, 1.0f);
                int ms = dur.containsKey(k) ? dur.get(k) : 400;
                cursor++;
                if (cursor < queue.size()) handler.postDelayed(this, Math.max(120, ms - 30));
            }
        };
        handler.post(chain);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        queue.clear();
        try { if (sp != null) { sp.stop(1); sp.stop(2); } } catch (Throwable e) { }
    }

    public void release() {
        stop();
        try { if (sp != null) sp.release(); } catch (Throwable e) { }
        sp = null;
    }
}
