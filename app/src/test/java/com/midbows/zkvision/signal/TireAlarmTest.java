package com.midbows.zkvision.signal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TireAlarmTest {

    @Test
    public void warningStatesTrue() {
        assertTrue(TireAlarm.isWarning(TireAlarm.TPMS_STATES_CMN_WARN));
        assertTrue(TireAlarm.isWarning(TireAlarm.TPMS_STATES_FL_WARN));
        assertTrue(TireAlarm.isWarning(TireAlarm.TPMS_STATES_RR_WARN));
    }

    @Test
    public void noWarnAndUnknownFalse() {
        assertFalse(TireAlarm.isWarning(TireAlarm.TPMS_STATES_NO_WARN));
        assertFalse(TireAlarm.isWarning(0));
        assertFalse(TireAlarm.isWarning(5259272)); // SYS_FAILURE
    }
}
