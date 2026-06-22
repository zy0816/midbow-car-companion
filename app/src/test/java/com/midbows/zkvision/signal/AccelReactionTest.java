package com.midbows.zkvision.signal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccelReactionTest {

    private static final float A = 2.5f;
    private static final float B = 3.0f;

    @Test
    public void hardAccelerationDetected() {
        assertEquals(AccelReaction.ACCEL, AccelReaction.classify(3.0f, A, B));
        assertEquals(AccelReaction.ACCEL, AccelReaction.classify(2.5f, A, B));
    }

    @Test
    public void hardBrakingDetected() {
        assertEquals(AccelReaction.BRAKE, AccelReaction.classify(-3.5f, A, B));
        assertEquals(AccelReaction.BRAKE, AccelReaction.classify(-3.0f, A, B));
    }

    @Test
    public void gentleDrivingIsNone() {
        assertEquals(AccelReaction.NONE, AccelReaction.classify(0f, A, B));
        assertEquals(AccelReaction.NONE, AccelReaction.classify(1.5f, A, B));
        assertEquals(AccelReaction.NONE, AccelReaction.classify(-2.0f, A, B));
    }
}
