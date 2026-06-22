package com.midbows.zkvision.signal;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 导航转弯联动：解析高德/百度车机导航广播，临近路口时触发机器人转向。
 */
public final class NavMonitor extends BroadcastSignalSource {

    private static final String TAG = "NavMonitor";
    private static final int TURN_DISTANCE_M = 150;

    public NavMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void fillFilter(IntentFilter filter) {
        filter.addAction("com.autonavi.amapauto.naviinfo");
        filter.addAction("autonavi.amapauto.navigation.info");
        filter.addAction("com.baidu.navi.action.NAVINFO");
    }

    @Override
    protected void onAction(String action, Intent intent) {
        if (!"com.autonavi.amapauto.naviinfo".equalsIgnoreCase(action)) {
            return;
        }
        int icon = intent.getIntExtra("ICON", 0);
        int segDist = intent.getIntExtra("SEG_DIST", Integer.MAX_VALUE);
        if (segDist >= TURN_DISTANCE_M) {
            return;
        }
        if (NavTurns.isLeft(icon)) {
            engine.onNavTurn("left");
        } else if (NavTurns.isRight(icon)) {
            engine.onNavTurn("right");
        }
    }
}
