import { media } from '@kit.MediaKit';
import { common } from '@kit.AbilityKit';

/**
 * 内置离线中文播报（素材随 HAP 打包，不依赖系统 TTS）
 * 素材放在 resources/rawfile/voice/*.mp3，键名与安卓版一致（n_ 前缀）。
 * 播放采用“一条一段、按素材时长排队”的方式，保证语音不叠话。
 */
const DUR_TABLE: string =
  'w0:792 w1:1056 w2:1008 w3:696 s2:816 s3:960 s4:888 s5:1008 s6:864 s7:888 s8:816 s9:864 ' +
  's10:960 s11:840 s12:984 s13:1056 s14:864 s15:1080 s16:960 s17:840 u1:480 u2:1032 u3:672 u4:552 ' +
  'u5:744 u6:720 u7:768 u8:816 u9:792 u10:696 b4:1008 b5:1032 b6:1056 b7:1128 b8:984 z1:912 z2:888 ' +
  'd1:648 d2:792 d3:960 g1:624 g2:816 g3:1080 g4:792 f1:864 f2:1056 p1:744 p2:936 p3:888 p4:1008 ' +
  'y1:720 y2:600 t1:864 t2:1056';

export class VoicePlayer {
  private ctx: common.UIAbilityContext;
  private dur: Map<string, number> = new Map<string, number>();
  private queue: string[] = [];
  private cur: boolean = false;
  private enabled: boolean = true;
  private released: boolean = false;

  constructor(ctx: common.UIAbilityContext) {
    this.ctx = ctx;
    const parts: string[] = DUR_TABLE.split(' ');
    for (let i = 0; i < parts.length; i++) {
      const kv: string[] = parts[i].split(':');
      if (kv.length === 2) {
        this.dur.set(kv[0], parseInt(kv[1], 10));
      }
    }
  }

  setEnabled(on: boolean): void {
    this.enabled = on;
    if (!on) {
      this.queue = [];
      this.cur = false;
    }
  }

  isBusy(): boolean {
    return this.cur || this.queue.length > 0;
  }

  /** 当前排队语音剩余时长（毫秒），用于决定下一位何时动手 */
  remainingMs(): number {
    let ms: number = 0;
    if (this.cur) ms += 600;
    for (let i = 0; i < this.queue.length; i++) ms += this.durOf(this.queue[i]);
    return ms;
  }

  say(keys: string[]): void {
    if (!this.enabled || this.released || keys.length === 0) return;
    for (let i = 0; i < keys.length; i++) {
      if (keys[i].length > 0) this.queue.push(keys[i]);
    }
    this.pump();
  }

  private durOf(key: string): number {
    const v: number | undefined = this.dur.get(key);
    if (v === undefined) return 700;
    return v;
  }

  private pump(): void {
    if (this.cur) return;
    if (this.queue.length === 0) return;
    const key: string = this.queue[0];
    this.queue.shift();
    this.cur = true;
    this.playFile(key);
    const ms: number = this.durOf(key) + 110;
    setTimeout(() => {
      this.cur = false;
      this.pump();
    }, ms);
  }

  private playFile(key: string): void {
    const run = async (): Promise<void> => {
      let player: media.AVPlayer | null = null;
      try {
        player = await media.createAVPlayer();
        const fd = await this.ctx.resourceManager.getRawFd('voice/n_' + key + '.mp3');
        const p: media.AVPlayer = player;
        p.on('stateChange', (state: media.AVPlayerState) => {
          if (state === 'initialized') {
            p.prepare();
          } else if (state === 'prepared') {
            p.play();
          } else if (state === 'completed') {
            p.release();
          }
        });
        p.on('error', () => {
          p.release();
        });
        p.fdSrc = { fd: fd.fd, offset: fd.offset, length: fd.length };
      } catch (e) {
        if (player !== null) {
          player.release();
        }
      }
    };
    run();
  }

  shutdown(): void {
    this.released = true;
    this.queue = [];
    this.cur = false;
  }

  /** 随机挑一个片段 */
  static pick(keys: string[]): string {
    if (keys.length === 0) return '';
    const i: number = Math.floor(Math.random() * keys.length);
    return keys[i];
  }
}
