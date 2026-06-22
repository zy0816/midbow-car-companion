package com.midbows.zkvision.protocol;

import static org.junit.Assert.assertArrayEquals;

import com.midbows.zkvision.util.HexUtil;

import org.junit.Test;

public class EyesProtocolTest {

    @Test
    public void setRgb_isBgrOrder() {
        // R=0x11 G=0x22 B=0x33 brightness=0x44 seq=0x05
        // 期望 A1 14 05 04 [B=33] [G=22] [R=11] [44]
        assertArrayEquals(HexUtil.fromHex("A114050433221144"),
                EyesProtocol.setRgb(0x11, 0x22, 0x33, 0x44, 0x05));
    }

    @Test
    public void command_withPayload_setsLength() {
        assertArrayEquals(HexUtil.fromHex("A11407020A0B"),
                EyesProtocol.command(EyesProtocol.CMD_SET_RGB, 0x07, new byte[]{0x0A, 0x0B}));
    }

    @Test
    public void command_nullPayload_zeroLength() {
        assertArrayEquals(HexUtil.fromHex("A1060300"),
                EyesProtocol.command(EyesProtocol.CMD_PREV_ITEM, 0x03, null));
    }

    @Test
    public void command_emptyPayload_zeroLength() {
        assertArrayEquals(HexUtil.fromHex("A1070800"),
                EyesProtocol.command(EyesProtocol.CMD_NEXT_ITEM, 0x08, new byte[0]));
    }
}
