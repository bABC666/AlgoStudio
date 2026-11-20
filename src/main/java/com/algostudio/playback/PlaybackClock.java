package com.algostudio.playback;

/** 串行时钟端口；UI 适配器保证回调和播放器命令在同一 FX 线程执行。 */
public interface PlaybackClock {
    void start(Runnable tick);
    void stop();
    void setSpeed(double multiplier);
    void dispose();
}
