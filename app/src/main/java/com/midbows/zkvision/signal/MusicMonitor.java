package com.midbows.zkvision.signal;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

import java.util.List;

/**
 * 音乐律动：跟随车机是否在<b>真的放音乐</b>，开/关运动板固件自适应律动（摇头晃脑）。
 *
 * <p><b>关键修正</b>：旧版用 {@link AudioManager#isMusicActive()} 判定，但它对<b>任何</b>占用
 * 音频流的声音都返回 true——导航 TTS 语音播报、系统提示音、ADAS 提示音都算。于是行车中导航
 * 报一句「前方右转」就让机器人莫名其妙开始律动（开了固件自适应律动后，运动板还会跟着舱内
 * 声音持续乱跳）。
 *
 * <p>改为读系统<b>活动媒体会话</b>（{@link MediaSessionManager}，平台签名授予
 * {@code MEDIA_CONTENT_CONTROL}）：仅当存在状态为 {@link PlaybackState#STATE_PLAYING}
 * 且音频用途为「媒体」的会话时才算在放音乐，按用途过滤掉导航/语音会话。再加停止去抖，
 * 骑过切歌/缓冲的短暂空隙，避免律动忽开忽关。
 */
public final class MusicMonitor extends AbstractSignalSource {

    private static final String TAG = "MusicMonitor";
    private static final long POLL_MS = 1000;
    /** 停止去抖：连续这么多次读到「无音乐」才判停，骑过切歌/缓冲空隙，避免律动忽开忽关。 */
    private static final int STOP_DEBOUNCE = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaSessionManager sessionManager;
    private AudioManager audioManager;
    private boolean playing;
    /** 连续读到「无音乐」的次数，达到 STOP_DEBOUNCE 才真正判停。 */
    private int silentCount;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (isMusicSessionPlaying()) {
                silentCount = 0;
                setPlaying(true);
            } else if (++silentCount >= STOP_DEBOUNCE) {
                setPlaying(false);
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    public MusicMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        sessionManager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        silentCount = 0;
        handler.post(poll);
    }

    @Override
    protected void doStop() {
        handler.removeCallbacks(poll);
        setPlaying(false);
    }

    /**
     * 是否存在「真的在放音乐」的活动媒体会话：状态为 PLAYING 且音频用途为媒体。
     * 按用途过滤掉导航引导/语音助手等非音乐会话，杜绝导航播报触发律动。
     */
    private boolean isMusicSessionPlaying() {
        if (sessionManager == null) {
            return false;
        }
        try {
            List<MediaController> controllers = sessionManager.getActiveSessions(null);
            for (MediaController c : controllers) {
                PlaybackState st = c.getPlaybackState();
                if (st == null || st.getState() != PlaybackState.STATE_PLAYING) {
                    continue;
                }
                if (isMediaUsage(c)) {
                    return true;
                }
            }
            return false;
        } catch (SecurityException e) {
            // 未授予 MEDIA_CONTENT_CONTROL（非平台签名）时退回旧判定，至少不彻底失效。
            RobotLog.w(TAG, "无媒体会话权限，回退 isMusicActive()：" + e.getMessage());
            return audioManager != null && audioManager.isMusicActive();
        }
    }

    /** 会话音频用途是否为「媒体」（排除导航引导 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE、语音助手等）。 */
    private boolean isMediaUsage(MediaController c) {
        MediaController.PlaybackInfo info = c.getPlaybackInfo();
        if (info == null) {
            // 拿不到用途信息时保守认可（多数纯音乐播放器，宁可律动也别漏）。
            return true;
        }
        AudioAttributes attrs = info.getAudioAttributes();
        if (attrs == null) {
            return true;
        }
        int usage = attrs.getUsage();
        return usage == AudioAttributes.USAGE_MEDIA
                || usage == AudioAttributes.USAGE_UNKNOWN;
    }

    private synchronized void setPlaying(boolean play) {
        if (playing == play) {
            return;
        }
        playing = play;
        RobotLog.d(TAG, "车机播放状态: " + (play ? "播放" : "停止"));
        if (play) {
            engine.onMusicStart();
        } else {
            engine.onMusicStop();
        }
    }
}
