package com.midbows.zkvision.signal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavTurnsTest {

    @Test
    public void leftIcons() {
        for (int icon : new int[]{2, 4, 6, 8}) {
            assertTrue("icon=" + icon, NavTurns.isLeft(icon));
            assertFalse("icon=" + icon, NavTurns.isRight(icon));
        }
    }

    @Test
    public void rightIcons() {
        for (int icon : new int[]{3, 5, 7}) {
            assertTrue("icon=" + icon, NavTurns.isRight(icon));
            assertFalse("icon=" + icon, NavTurns.isLeft(icon));
        }
    }

    @Test
    public void neitherForStraightOrUnknown() {
        for (int icon : new int[]{0, 1, 9, 99}) {
            assertFalse("icon=" + icon, NavTurns.isLeft(icon));
            assertFalse("icon=" + icon, NavTurns.isRight(icon));
        }
    }
}
