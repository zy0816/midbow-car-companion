package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 车机语音助手联动（免授权通道）：从系统日志识别语音状态，从语音守护进程的
 * SharedPreferences 读唤醒方向。
 *
 * <p>不依赖任何闭源语音 SDK——车机统一核心服务对三方应用有密码学授权门槛
 * （需厂商私钥签发的 license），无法绕过；故改走两条免授权旁路：
 * <ul>
 *   <li><b>状态</b>：起一个 {@code logcat} 子进程，按事件主题字串匹配 唤醒/聆听/说话/空闲；</li>
 *   <li><b>方向</b>：唤醒时直接读语音守护进程 shared_prefs 里的方向键（同 system uid 可读）。</li>
 * </ul>
 * 车型相关的包名/文件名/主题字串集中在 {@link SpeechSignalConfig}（不入库，移植时按车型填写）。
 * 需 {@code READ_LOGS} 权限（平台签名授予）。非车机环境或取不到时静默降级，不抛异常。
 */
public final class LogcatVoiceMonitor extends AbstractSignalSource {

    private static final String TAG = "VoiceMonitor";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Pattern directionPattern = Pattern.compile(
            Pattern.quote(SpeechSignalConfig.WAKEUP_DIRECTION_KEY) + "\">\\s*(\\d+)\\s*<");

    private Process logcatProcess;
    private Thread readerThread;

    /** 状态去抖：仅在状态发生变化时下发，避免同状态日志反复触发。 */
    private volatile String lastState;

    public LogcatVoiceMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        lastState = null;
        readerThread = new Thread(this::pumpLogcat, "voice-logcat");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void pumpLogcat() {
        try {
            // -v raw 只要消息体；-T 1 从「现在」起读，不回放历史。
            logcatProcess = new ProcessBuilder("logcat", "-v", "raw", "-T", "1")
                    .redirectErrorStream(true)
                    .start();
            RobotLog.d(TAG, "logcat 通道已启动");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while (isRunning() && (line = reader.readLine()) != null) {
                handleLine(line);
            }
        } catch (Throwable t) {
            if (isRunning()) {
                RobotLog.w(TAG, "logcat 通道异常，语音联动降级: " + t.getMessage());
            }
        }
    }

    private void handleLine(String line) {
        if (line.contains(SpeechSignalConfig.TOPIC_WAKEUP)) {
            onWakeup();
        } else if (line.contains(SpeechSignalConfig.TOPIC_LISTENING)) {
            dispatchState("listening");
        } else if (line.contains(SpeechSignalConfig.TOPIC_SPEAKING)) {
            dispatchState("speaking");
        } else if (line.contains(SpeechSignalConfig.TOPIC_SPEAKING_END)
                || line.contains(SpeechSignalConfig.TOPIC_IDLE)) {
            dispatchState("idle");
        }
    }

    private void onWakeup() {
        // 唤醒是一次新会话的起点，清掉上轮状态以便后续状态都能重新下发。
        lastState = null;
        int seat = WakeupDirection.toSeat(readWakeupDirection());
        RobotLog.d(TAG, "唤醒，转向座位 " + seat);
        main.post(() -> engine.onVoiceWakeup(seat));
    }

    private void dispatchState(String state) {
        if (state.equals(lastState)) {
            return;
        }
        lastState = state;
        RobotLog.d(TAG, "语音状态=" + state);
        main.post(() -> engine.onVoiceStateChanged(state));
    }

    /** 从语音守护进程的 SharedPreferences 读唤醒方向；读不到返回 0（居中）。 */
    private int readWakeupDirection() {
        File sp = new File("/data/data/" + SpeechSignalConfig.DAEMON_PACKAGE
                + "/shared_prefs/" + SpeechSignalConfig.KEYS_SP_FILE);
        if (!sp.canRead()) {
            return 0;
        }
        try (FileInputStream in = new FileInputStream(sp)) {
            byte[] buf = new byte[(int) sp.length()];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            String xml = new String(buf, 0, read, StandardCharsets.UTF_8);
            Matcher m = directionPattern.matcher(xml);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Throwable t) {
            RobotLog.d(TAG, "读唤醒方向失败: " + t.getMessage());
        }
        return 0;
    }

    @Override
    protected void doStop() {
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
        lastState = null;
    }
}
