package com.midbows.zkvision.signal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AmbientColorMapTest {

    @Test
    public void knownColorsMapToRgb() {
        assertArrayEquals(new int[]{255, 40, 40},
                AmbientColorMap.toRgb(AmbientColorMap.MOOD_LIGHT_COLOR_RED));
        assertArrayEquals(new int[]{0, 120, 255},
                AmbientColorMap.toRgb(AmbientColorMap.MOOD_LIGHT_COLOR_BLUE));
        assertArrayEquals(new int[]{255, 255, 255},
                AmbientColorMap.toRgb(AmbientColorMap.MOOD_LIGHT_COLOR_WHITE));
    }

    @Test
    public void offAndUnknownReturnNull() {
        assertNull(AmbientColorMap.toRgb(AmbientColorMap.MOOD_LIGHT_COLOR_OFF));
        assertNull(AmbientColorMap.toRgb(0));
        assertNull(AmbientColorMap.toRgb(123456));
    }
}
