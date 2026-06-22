package com.midbows.zkvision.signal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CabinTempReactionTest {

    private static final float COLD = 16f;
    private static final float HOT = 30f;

    @Test
    public void coldHotNormalBoundaries() {
        assertEquals(CabinTempReaction.COLD, CabinTempReaction.classify(10f, COLD, HOT));
        assertEquals(CabinTempReaction.COLD, CabinTempReaction.classify(16f, COLD, HOT));
        assertEquals(CabinTempReaction.HOT, CabinTempReaction.classify(35f, COLD, HOT));
        assertEquals(CabinTempReaction.HOT, CabinTempReaction.classify(30f, COLD, HOT));
        assertEquals(CabinTempReaction.NORMAL, CabinTempReaction.classify(22f, COLD, HOT));
    }
}
