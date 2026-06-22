package com.midbows.zkvision.signal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrivePersonalityTest {

    @Test
    public void sportModesAreEnergetic() {
        assertEquals(DrivePersonality.SPORT, DrivePersonality.of(570491157)); // SPORT_PLUS
        assertEquals(DrivePersonality.SPORT, DrivePersonality.of(570491144)); // POWER
        assertTrue(DrivePersonality.SPORT.energetic);
    }

    @Test
    public void ecoAndOffroadAndSnowMapped() {
        assertEquals(DrivePersonality.ECO, DrivePersonality.of(570491137));     // ECO
        assertEquals(DrivePersonality.SNOW, DrivePersonality.of(570491145));    // SNOW
        assertEquals(DrivePersonality.OFFROAD, DrivePersonality.of(570491155)); // OFFROAD
    }

    @Test
    public void unknownFallsBackToComfort() {
        assertEquals(DrivePersonality.COMFORT, DrivePersonality.of(0));
        assertEquals(DrivePersonality.COMFORT, DrivePersonality.of(570491138)); // COMFORT
        assertEquals(DrivePersonality.COMFORT, DrivePersonality.of(999));
    }
}
