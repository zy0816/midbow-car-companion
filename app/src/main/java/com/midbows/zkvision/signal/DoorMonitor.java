package com.midbows.zkvision.signal;

import android.content.Context;

import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 上车问候：监听车门开启触发问候。
 *
 * <p>改用车机原生 ecarx 车控 API：{@link EcarxDoorWatcher}
 * 监听真实车门功能 {@code BCM_FUNC_DOOR}，车门打开即判定上车。本类纯粹包装 watcher 生命周期。
 */
public final class DoorMonitor extends AbstractSignalSource {

    private static final String TAG = "DoorMonitor";

    private final EcarxDoorWatcher carWatcher;

    public DoorMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
        this.carWatcher = new EcarxDoorWatcher(context, new EcarxDoorWatcher.DoorListener() {
            @Override
            public void onDoorOpen(int zone) {
                DoorMonitor.this.onDoorOpen(zone);
            }

            @Override
            public void onAllDoorsClosed() {
                engine.onDoorClosed();
            }
        });
    }

    /** 车门 zone → 座位：1=主驾(左) 4=副驾(右)，其它居中。 */
    private void onDoorOpen(int zone) {
        int seat;
        if (zone == 1) {
            seat = 1; // 主驾，向左
        } else if (zone == 4) {
            seat = 2; // 副驾，向右
        } else {
            seat = 0; // 后排/未知，居中
        }
        engine.onDoorWelcome(seat);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        carWatcher.start();
    }

    @Override
    protected void doStop() {
        carWatcher.stop();
    }
}
