package com.midbows.zkvision.signal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WakeupDirectionTest {

    @Test
    public void leftSideSeatsTurnLeft() {
        assertEquals(WakeupDirection.SEAT_LEFT, WakeupDirection.toSeat(WakeupDirection.DRIVER));
        assertEquals(WakeupDirection.SEAT_LEFT, WakeupDirection.toSeat(WakeupDirection.REAR_LEFT));
    }

    @Test
    public void rightSideSeatsTurnRight() {
        assertEquals(WakeupDirection.SEAT_RIGHT, WakeupDirection.toSeat(WakeupDirection.COPILOT));
        assertEquals(WakeupDirection.SEAT_RIGHT, WakeupDirection.toSeat(WakeupDirection.REAR_RIGHT));
    }

    @Test
    public void unknownOrZeroCentersRobot() {
        for (int direction : new int[]{0, -1, 5, 6, 99}) {
            assertEquals("direction=" + direction,
                    WakeupDirection.SEAT_CENTER, WakeupDirection.toSeat(direction));
        }
    }
}
