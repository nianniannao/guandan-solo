#!/usr/bin/env python3
"""生成内置语音素材。

按下面的命名规则生成 mp3（16kHz 单声道 32kbps）到 app/src/main/assets/voice/，
同时写出 index.txt（片段名 + 毫秒时长），供 LocalVoice 顺序拼接播放。

你需要自备一个中文语音合成来源（本地 TTS、自建服务或你自己的录音），
把合成结果放到 /tmp/vraw/<片段名>.mp3，再运行本脚本完成压缩与索引。
"""
import os, subprocess

KEYS = ['w0', 'w1', 'w2', 'w3', 'out']
for t in ['s', 'p', 't', 'h']:
    KEYS += ['%s%d' % (t, r) for r in range(2, 15)]
    KEYS += ['%slv' % t, '%sxw' % t, '%sdw' % t]
KEYS += ['straight', 'cp', 'plate', 'flush', 'king',
         'b四', 'b五', 'b六', 'b七', 'b八', 'pass', 'turn']

RAW = '/tmp/vraw'
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'voice')

def main():
    os.makedirs(OUT, exist_ok=True)
    dur = {}
    for k in KEYS:
        src = os.path.join(RAW, k + '.mp3')
        if not os.path.exists(src):
            raise SystemExit('缺少素材：%s' % src)
        dst = os.path.join(OUT, k + '.mp3')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', src,
                        '-ac', '1', '-ar', '16000', '-b:a', '32k',
                        '-af', 'volume=1.8', dst], check=True)
        d = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                            'format=duration', '-of', 'csv=p=0', dst],
                           capture_output=True, text=True).stdout.strip()
        dur[k] = int(float(d) * 1000)
    with open(os.path.join(OUT, 'index.txt'), 'w') as f:
        f.write('\n'.join('%s %d' % (k, dur[k]) for k in KEYS))
    print('生成 %d 个片段' % len(dur))

if __name__ == '__main__':
    main()
