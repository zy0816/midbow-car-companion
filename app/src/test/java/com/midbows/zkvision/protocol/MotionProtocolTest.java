package com.midbows.zkvision.protocol;

import static org.junit.Assert.assertArrayEquals;

import com.midbows.zkvision.util.HexUtil;

import org.junit.Test;

public class MotionProtocolTest {

    @Test
    public void frame_up() {
        assertArrayEquals(HexUtil.fromHex("FE55100155FE"),
                MotionProtocol.frame(MotionProtocol.DIR_UP));
    }

    @Test
    public void frame_down() {
        assertArrayEquals(HexUtil.fromHex("FE55100255FE"),
                MotionProtocol.frame(MotionProtocol.DIR_DOWN));
    }

    @Test
    public void frame_left() {
        assertArrayEquals(HexUtil.fromHex("FE55100355FE"),
                MotionProtocol.frame(MotionProtocol.DIR_LEFT));
    }

    @Test
    public void frame_right() {
        assertArrayEquals(HexUtil.fromHex("FE55100455FE"),
                MotionProtocol.frame(MotionProtocol.DIR_RIGHT));
    }

    @Test
    public void frame_center() {
        assertArrayEquals(HexUtil.fromHex("FE55100555FE"),
                MotionProtocol.frame(MotionProtocol.DIR_CENTER));
    }

    @Test
    public void frame_motorOn() {
        assertArrayEquals(HexUtil.fromHex("FE5510CE55FE"),
                MotionProtocol.frame(MotionProtocol.MOTOR_ON));
    }

    @Test
    public void frame_motorOffSequence() {
        assertArrayEquals(HexUtil.fromHex("FE5510E455FE"),
                MotionProtocol.frame(MotionProtocol.MOTOR_OFF_PRE));
        assertArrayEquals(HexUtil.fromHex("FE5510CF55FE"),
                MotionProtocol.frame(MotionProtocol.MOTOR_OFF));
    }

    @Test
    public void frame_rhythm() {
        assertArrayEquals(HexUtil.fromHex("FE5510E055FE"),
                MotionProtocol.frame(MotionProtocol.RHYTHM_SLOWER));
        assertArrayEquals(HexUtil.fromHex("FE5510E155FE"),
                MotionProtocol.frame(MotionProtocol.RHYTHM_FASTER));
        assertArrayEquals(HexUtil.fromHex("FE5510F055FE"),
                MotionProtocol.frame(MotionProtocol.RHYTHM_ADAPTIVE_ON));
        assertArrayEquals(HexUtil.fromHex("FE5510F255FE"),
                MotionProtocol.frame(MotionProtocol.RHYTHM_ADAPTIVE_OFF));
    }

    @Test
    public void frame_alwaysSixBytesWithFixedHeaderTail() {
        byte[] f = MotionProtocol.frame((byte) 0x00);
        assertArrayEquals(HexUtil.fromHex("FE55100055FE"), f);
    }
}
