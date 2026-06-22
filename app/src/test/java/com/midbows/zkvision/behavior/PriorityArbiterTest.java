package com.midbows.zkvision.behavior;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PriorityArbiterTest {

    @Test
    public void startsIdle() {
        assertEquals(PriorityArbiter.IDLE, new PriorityArbiter().current());
    }

    @Test
    public void higherPriorityWins() {
        PriorityArbiter a = new PriorityArbiter();
        assertTrue(a.accept(PriorityArbiter.MUSIC));
        assertTrue(a.accept(PriorityArbiter.WAKEUP));
        assertEquals(PriorityArbiter.WAKEUP, a.current());
    }

    @Test
    public void lowerPriorityIgnoredWhileHigherActive() {
        PriorityArbiter a = new PriorityArbiter();
        a.accept(PriorityArbiter.WAKEUP);
        assertFalse(a.accept(PriorityArbiter.MUSIC));
        assertEquals(PriorityArbiter.WAKEUP, a.current());
    }

    @Test
    public void samePriorityRefreshes() {
        PriorityArbiter a = new PriorityArbiter();
        assertTrue(a.accept(PriorityArbiter.TTS));
        assertTrue(a.accept(PriorityArbiter.TTS));
    }

    @Test
    public void resetIfAt_onlyResetsMatchingPriority() {
        PriorityArbiter a = new PriorityArbiter();
        a.accept(PriorityArbiter.NAV);
        assertFalse(a.resetIfAt(PriorityArbiter.TTS));
        assertEquals(PriorityArbiter.NAV, a.current());
        assertTrue(a.resetIfAt(PriorityArbiter.NAV));
        assertEquals(PriorityArbiter.IDLE, a.current());
    }

    @Test
    public void musicCanRetriggerFromIdleOrMusic() {
        PriorityArbiter a = new PriorityArbiter();
        assertTrue(a.accept(PriorityArbiter.MUSIC)); // from IDLE
        assertTrue(a.accept(PriorityArbiter.MUSIC)); // refresh at MUSIC
    }

    @Test
    public void reset_goesToIdle() {
        PriorityArbiter a = new PriorityArbiter();
        a.accept(PriorityArbiter.WAKEUP);
        a.reset();
        assertEquals(PriorityArbiter.IDLE, a.current());
    }
}
