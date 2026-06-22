package com.midbows.zkvision.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.midbows.zkvision.util.RobotLog;

/**
 * 开机自启：系统启动完成后拉起 {@link RobotService}，免去每次手动打开 app。
 *
 * <p>车机为平台签名应用，BOOT_COMPLETED 广播可直接收到；O 及以上用 startForegroundService
 * 拉前台服务，避免后台启动限制。
 */
public final class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }
        RobotLog.d(TAG, "收到开机广播，拉起 RobotService");
        Intent service = new Intent(context, RobotService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
