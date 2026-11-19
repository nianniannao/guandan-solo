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

    /** 素材命名空间前缀：换掉后缀可让旧缓存文件彻底失效 */
    public static final String KEY = "n_";

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
    private long totalMs;
    private int streamId;

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

        // 先读索引，并用索引内容做指纹：素材一旦换过，就整目录重解压，
        // 避免旧版本的同名片段（如 p4 旧义"一对四"）残留导致播错话
        InputStream in = am.open("voice/index.txt");
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
        StringBuilder raw = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) continue;
            raw.append(line);
            String[] p = line.split(" ");
            if (p.length != 2) continue;
            dur.put(p[0], Integer.parseInt(p[1]));
        }
        br.close();
        String stamp = Integer.toHexString(raw.toString().hashCode()) + "-" + dur.size();

        File dir = new File(ctx.getFilesDir(), "voice");
        if (!dir.exists()) dir.mkdirs();
        File mark = new File(dir, ".stamp");
        // 程序升级（versionCode 变化）就整目录重建，杜绝任何旧版本残留
        String ver = "0";
        try { ver = String.valueOf(ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode); } catch (Throwable e) { }
        File verFile = new File(dir, ".ver");
        String oldVer = "";
        if (verFile.exists() && verFile.length() < 64) {
            try {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                java.io.FileInputStream fi = new java.io.FileInputStream(verFile);
                byte[] b = new byte[32]; int n;
                while ((n = fi.read(b)) > 0) bo.write(b, 0, n);
                fi.close();
                oldVer = new String(bo.toByteArray(), "UTF-8").trim();
            } catch (Throwable e) { }
        }
        if (!ver.equals(oldVer)) {
            File[] vs = dir.listFiles();
            if (vs != null) for (File f : vs) { if (f.isFile()) f.delete(); }
        }
        String oldStamp = "";
        if (mark.exists() && mark.length() < 512) {
            try {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                java.io.FileInputStream fi = new java.io.FileInputStream(mark);
                byte[] b = new byte[256];
                int n;
                while ((n = fi.read(b)) > 0) bo.write(b, 0, n);
                fi.close();
                oldStamp = new String(bo.toByteArray(), "UTF-8").trim();
            } catch (Throwable e) { }
        }
        if (!stamp.equals(oldStamp)) {
            File[] olds = dir.listFiles();
            if (olds != null) for (File f : olds) { if (f.isFile()) f.delete(); }
        }

        for (int i = 0; i < files.length; i++) {
            String n = files[i];
            if (!n.endsWith(".mp3")) continue;
            File out = new File(dir, n);
            if (!out.exists() || out.length() < 500) {
                InputStream ins = am.open("voice/" + n);
                OutputStream os = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int r;
                while ((r = ins.read(buf)) > 0) os.write(buf, 0, r);
                os.close();
                ins.close();
            }
        }
        // 指纹写入最后，避免半途失败留下"已完成"的假状态
        try {
            OutputStream mo = new FileOutputStream(mark);
            mo.write(stamp.getBytes("UTF-8"));
            mo.close();
        } catch (Throwable e) { }
        try {
            OutputStream vo = new FileOutputStream(verFile);
            vo.write(ver.getBytes("UTF-8"));
            vo.close();
        } catch (Throwable e) { }

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
        stopStreams();
        queue.clear();
        totalMs = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) continue;
            String k = KEY + parts[i];
            if (pool.containsKey(k)) {
                queue.add(k);
                totalMs += dur.containsKey(k) ? dur.get(k) : 400;
            }
        }
        cursor = 0;
        chain = new Runnable() {
            public void run() {
                if (cursor >= queue.size()) return;
                String k = queue.get(cursor);
                Integer id = pool.get(k);
                if (id != null) {
                    stopStreams();
                    streamId = sp.play(id, 1.0f, 1.0f, 1, 0, 1.0f);
                }
                int ms = dur.containsKey(k) ? dur.get(k) : 400;
                cursor++;
                if (cursor < queue.size()) handler.postDelayed(this, Math.max(120, ms - 30));
            }
        };
        handler.post(chain);
    }

    /** 上一句播报的总时长（毫秒），用于节奏控制 */
    public long lastDurationMs() { return totalMs; }

    private void stopStreams() {
        try { if (sp != null && streamId != 0) { sp.stop(streamId); streamId = 0; } } catch (Throwable e) { }
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        queue.clear();
        stopStreams();
    }

    public void release() {
        stop();
        try { if (sp != null) sp.release(); } catch (Throwable e) { }
        sp = null;
    }
}
