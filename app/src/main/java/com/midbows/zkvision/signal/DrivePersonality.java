package com.midbows.zkvision.signal;

/**
 * 车机驾驶模式 → 机器人人格（颜色 + 是否活泼）的纯映射（可单测）。
 *
 * <p>车机 {@code IDriveMode.DM_FUNC_DRIVE_MODE_SELECT} 取值为 {@code DRIVE_MODE_SELECTION_*}。
 * 用字面常量复刻 ecarx 值，保持纯逻辑、不依赖 ecarx jar。颜色为 RGB + 亮度，
 * {@code energetic} 指示编排是否走「更快节奏」。
 */
public enum DrivePersonality {

    /** 运动：热烈红、活泼。 */
    SPORT(255, 40, 30, 230, true),
    /** 节能：清新绿、沉稳。 */
    ECO(0, 200, 90, 180, false),
    /** 舒适/普通：柔和蓝。 */
    COMFORT(0, 140, 255, 180, false),
    /** 雪地：冰白蓝、谨慎。 */
    SNOW(180, 220, 255, 200, false),
    /** 越野：大地橙、稳健。 */
    OFFROAD(255, 120, 20, 200, false);

    public final int r;
    public final int g;
    public final int b;
    public final int brightness;
    public final boolean energetic;

    DrivePersonality(int r, int g, int b, int brightness, boolean energetic) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.brightness = brightness;
        this.energetic = energetic;
    }

    // —— ecarx DRIVE_MODE_SELECTION_* 值（字面复刻）——
    private static final int SPORT_PLUS = 570491157;
    private static final int POWER = 570491144;
    private static final int DYNAMIC = 570491139;
    private static final int ECO_V = 570491137;
    private static final int ECO_PLUS = 570491156;
    private static final int SAVE = 570491151;
    private static final int PURE = 570491142;
    private static final int COMFORT_V = 570491138;
    private static final int NORMAL = 570491153;
    private static final int SNOW_V = 570491145;
    private static final int OFFROAD_V = 570491155;
    private static final int MUD = 570491146;
    private static final int SAND = 570491149;
    private static final int ROCK = 570491147;
    private static final int AWD = 570491150;
    private static final int HDC = 570491141;

    /** 把车机驾驶模式值映射为人格；未知归 {@link #COMFORT}。 */
    public static DrivePersonality of(int driveModeValue) {
        switch (driveModeValue) {
            case SPORT_PLUS:
            case POWER:
            case DYNAMIC:
                return SPORT;
            case ECO_V:
            case ECO_PLUS:
            case SAVE:
            case PURE:
                return ECO;
            case SNOW_V:
                return SNOW;
            case OFFROAD_V:
            case MUD:
            case SAND:
            case ROCK:
            case AWD:
            case HDC:
                return OFFROAD;
            case COMFORT_V:
            case NORMAL:
            default:
                return COMFORT;
        }
    }
}
