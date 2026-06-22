package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 车机语音助手联动：对接车机 VR SDK 的语音状态回调。
 *
 * <p>SDK 不在编译依赖里（车机 framework 提供），故全程<b>反射 + 动态代理</b>接入：
 * {@code VrAPI.get()} → {@code init(Context, ApiReadyCallback)}（必须主线程）→ 待
 * {@code onAPIReady(true, ...)} → {@code getConfigApi().registerVrStateCallback(AppInfo, IConfigStateCallback)}。
 * 回调 {@code vrStateChange(int)} 的状态码：0=空闲 1=聆听 2=说话开始 3=说话结束 4=唤醒 5=禁用 6=等待(思考)。
 *
 * <p>车机专有的 VR SDK 类名集中在 {@link VrSdkConfig}（不入库，移植时按车型填写）。需平台签名访问
 * VR 服务；非车机环境或类名不存在时静默降级，不抛异常。
 */
public final class VoiceAssistantMonitor extends AbstractSignalSource {

    private static final String TAG = "VoiceMonitor";

    // —— VR 状态码 ——
    private static final int VR_IDLE = 0;
    private static final int VR_LISTENING = 1;
    private static final int VR_SPEAKING_START = 2;
    private static final int VR_SPEAKING_END = 3;
    private static final int VR_WAKEUP = 4;
    private static final int VR_DISABLE = 5;
    private static final int VR_WAITING = 6;

    private final Handler main = new Handler(Looper.getMainLooper());

    /** 强引用住，避免代理对象被回收。 */
    private Object vrApi;
    private Object configApi;
    private Object stateCallback;
    private int lastState = Integer.MIN_VALUE;

    public VoiceAssistantMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        lastState = Integer.MIN_VALUE;
        // init 必须主线程。
        main.post(this::initVrApi);
    }

    private void initVrApi() {
        try {
            Class<?> vrApiCls = Class.forName(VrSdkConfig.VR_API);
            vrApi = vrApiCls.getMethod("get").invoke(null);
            Class<?> readyCls = Class.forName(VrSdkConfig.API_READY_CALLBACK);
            Object ready = Proxy.newProxyInstance(
                    readyCls.getClassLoader(), new Class<?>[]{readyCls}, new ReadyHandler());
            vrApi.getClass().getMethod("init", Context.class, readyCls)
                    .invoke(vrApi, context.getApplicationContext(), ready);
            RobotLog.w(TAG, "VR SDK init 已调用，等待 onAPIReady…");
        } catch (Throwable t) {
            RobotLog.w(TAG, "VR SDK 不可用，语音联动降级: " + t.getMessage());
        }
    }

    private void onApiReady() {
        try {
            configApi = vrApi.getClass().getMethod("getConfigApi").invoke(vrApi);
            if (configApi == null) {
                RobotLog.w(TAG, "getConfigApi 返回 null");
                return;
            }
            Class<?> appInfoCls = Class.forName(VrSdkConfig.APP_INFO);
            Constructor<?> ctor = appInfoCls.getConstructor(
                    String.class, String.class, String.class, String.class, int[].class);
            Object appInfo = ctor.newInstance(
                    "ZKVision", "5.0", context.getPackageName(), "车载机器人表情联动", new int[]{0});

            Class<?> cbCls = Class.forName(VrSdkConfig.CONFIG_STATE_CALLBACK);
            stateCallback = Proxy.newProxyInstance(
                    cbCls.getClassLoader(), new Class<?>[]{cbCls}, new StateHandler());

            Object ok = configApi.getClass()
                    .getMethod("registerVrStateCallback", appInfoCls, cbCls)
                    .invoke(configApi, appInfo, stateCallback);
            RobotLog.w(TAG, "registerVrStateCallback=" + ok);
        } catch (Throwable t) {
            RobotLog.w(TAG, "注册 VR 状态回调失败: " + t.getMessage());
        }
    }

    private void onVrState(int state) {
        if (state == lastState) {
            return;
        }
        lastState = state;
        RobotLog.w(TAG, "VR 状态=" + state);
        switch (state) {
            case VR_WAKEUP:
                engine.onVoiceWakeup(0);
                break;
            case VR_LISTENING:
                engine.onVoiceStateChanged("listening");
                break;
            case VR_WAITING:
                engine.onVoiceStateChanged("thinking");
                break;
            case VR_SPEAKING_START:
                engine.onVoiceStateChanged("speaking");
                break;
            case VR_SPEAKING_END:
            case VR_IDLE:
            case VR_DISABLE:
                engine.onVoiceStateChanged("idle");
                break;
            default:
                break;
        }
    }

    @Override
    protected void doStop() {
        if (configApi != null && stateCallback != null) {
            try {
                Class<?> cbCls = Class.forName(VrSdkConfig.CONFIG_STATE_CALLBACK);
                configApi.getClass().getMethod("unRegisterVrStateCallback", cbCls)
                        .invoke(configApi, stateCallback);
            } catch (Throwable t) {
                RobotLog.d(TAG, "注销 VR 状态回调失败: " + t.getMessage());
            }
        }
        configApi = null;
        stateCallback = null;
        vrApi = null;
        lastState = Integer.MIN_VALUE;
    }

    /** {@code ApiReadyCallback.onAPIReady(boolean, String)} 的动态代理处理器。 */
    private final class ReadyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("onAPIReady".equals(name)) {
                boolean ok = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
                RobotLog.w(TAG, "onAPIReady=" + ok);
                if (ok) {
                    main.post(VoiceAssistantMonitor.this::onApiReady);
                }
                return null;
            }
            return objectMethod(proxy, method, args);
        }
    }

    /** {@code IConfigStateCallback.vrStateChange(int)} 的动态代理处理器。 */
    private final class StateHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("vrStateChange".equals(method.getName()) && args != null && args.length > 0) {
                int state = (Integer) args[0];
                main.post(() -> onVrState(state));
                return null;
            }
            return objectMethod(proxy, method, args);
        }
    }

    /** 兜底 Object 方法（toString 被 SDK 当 map key 用，必须可用）。 */
    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return "ZKVisionVrProxy@" + System.identityHashCode(proxy);
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            default:
                return null;
        }
    }
}
