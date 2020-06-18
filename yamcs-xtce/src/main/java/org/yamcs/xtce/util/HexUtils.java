package org.yamcs.xtce.util;

public class HexUtils {

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            sb.append(String.format("%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Convert a hex string into a byte array. If the string has an odd number of hex digits, it is padded with 0 at the
     * <b>beginning</b>.
     * 
     * @param s
     *            - string to be converted
     * @return binary array representation of the hex string
     */
    public static byte[] unhex(String s) {
        if ((s.length() & 1) == 1) {
            s = "0" + s;
        }
        ;
        byte[] b = new byte[s.length() >> 1];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16) & 0xFF);
        }
        return b;
    }
}
