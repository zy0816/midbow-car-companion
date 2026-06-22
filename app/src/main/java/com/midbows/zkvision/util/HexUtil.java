package com.midbows.zkvision.util;

/**
 * Hex / byte 互转。全项目唯一实现，禁止在别处重复编写。
 */
public final class HexUtil {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HexUtil() {
    }

    /** byte[] -> 大写 16 进制字符串（无分隔）。 */
    public static String toHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(HEX[(b >> 4) & 0x0F]);
            sb.append(HEX[b & 0x0F]);
        }
        return sb.toString();
    }

    /** 16 进制字符串 -> byte[]。忽略空白，大小写不敏感。 */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String s = hex.replaceAll("\\s", "");
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex length must be even: " + hex);
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex char in: " + hex);
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
