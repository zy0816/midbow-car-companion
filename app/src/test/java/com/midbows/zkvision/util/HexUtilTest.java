package com.midbows.zkvision.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HexUtilTest {

    @Test
    public void toHex_normal() {
        assertEquals("FE5510CE55FE",
                HexUtil.toHex(new byte[]{(byte) 0xFE, 0x55, 0x10, (byte) 0xCE, 0x55, (byte) 0xFE}));
    }

    @Test
    public void toHex_null_returnsEmpty() {
        assertEquals("", HexUtil.toHex(null));
    }

    @Test
    public void toHex_empty() {
        assertEquals("", HexUtil.toHex(new byte[0]));
    }

    @Test
    public void fromHex_normal() {
        assertArrayEquals(new byte[]{(byte) 0xFE, 0x55, 0x10, (byte) 0xCE, 0x55, (byte) 0xFE},
                HexUtil.fromHex("FE5510CE55FE"));
    }

    @Test
    public void fromHex_ignoresWhitespace_caseInsensitive() {
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD},
                HexUtil.fromHex("ab cd"));
    }

    @Test
    public void fromHex_null_returnsEmpty() {
        assertArrayEquals(new byte[0], HexUtil.fromHex(null));
    }

    @Test
    public void roundTrip() {
        byte[] data = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF, 0x10};
        assertArrayEquals(data, HexUtil.fromHex(HexUtil.toHex(data)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromHex_oddLength_throws() {
        HexUtil.fromHex("ABC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromHex_invalidChar_throws() {
        HexUtil.fromHex("ZZ");
    }
}
