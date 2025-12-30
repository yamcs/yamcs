package org.yamcs.security.sdls;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Defines authentication masks to be used with SDLS, as per the CCSDS standard.
 */
public class StandardAuthMask {
    /**
     * @param priHdrLen           the length of the primary header
     * @param insZoneLen          the length of the insert zone
     * @param securityHdrAuthMask the auth mask to use on the SDLS security header
     * @return the authentication mask for use with USLP frames
     */
    public static byte[] USLP(int priHdrLen, int insZoneLen, byte[] securityHdrAuthMask) {
        ByteArrayOutputStream mask = new ByteArrayOutputStream();
        try {
            // First 6 fields (4 bytes) are always there
            mask.write(new byte[]{
                    0, 0,
                    // VCID, MAP ID
                    0b111, (byte) 0b1111_1110
            });
            for (int i = 0; i < priHdrLen - 4; ++i)
                mask.write(0);
            for (int i = 0; i < insZoneLen; ++i)
                mask.write(0);

            mask.write(securityHdrAuthMask);

            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param secHdrLen           the length of the secondary header
     * @param securityHdrAuthMask the auth mask to use on the SDLS security header
     * @return the authentication mask for use with TM frames
     */
    public static byte[] TM(int secHdrLen, byte[] securityHdrAuthMask) {
        ByteArrayOutputStream mask = new ByteArrayOutputStream();
        try {
            mask.write(new byte[]{
                    0,
                    // VCID
                    0b0000_1110,
                    0, 0, 0, 0,
            });
            for (int i = 0; i < secHdrLen; ++i)
                mask.write(0);

            mask.write(securityHdrAuthMask);
            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param frameHdrErrCtrl     whether Frame Header Error Control is present
     * @param insZoneLen          the length of the insert zone
     * @param securityHdrAuthMask the auth mask to use on the SDLS security header
     * @return the authentication mask for use with AOS frames
     */
    public static byte[] AOS(boolean frameHdrErrCtrl, int insZoneLen, byte[] securityHdrAuthMask) {
        ByteArrayOutputStream mask = new ByteArrayOutputStream();
        try {
            mask.write(new byte[]{
                            // No auth: TFVN, SCID
                            0,
                            // Auth VCID
                            0b0011_1111,
                            0, 0, 0, 0,
                    }
            );

            if (frameHdrErrCtrl) {
                mask.write(new byte[]{0, 0});
            }
            for (int i = 0; i < insZoneLen; ++i)
                mask.write(0);

            mask.write(securityHdrAuthMask);
            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param segHdr              whether the segment header is used
     * @param securityHdrAuthMask the auth mask to use on the SDLS security header
     * @return the authentication mask for use with TC frames
     */
    public static byte[] TC(boolean segHdr, byte[] securityHdrAuthMask) {
        try {
            ByteArrayOutputStream mask = new ByteArrayOutputStream();
            mask.write(new byte[]{
                    0, 0,
                    // VCID
                    (byte) 0b1111_1100, 0, 0,
            });
            if (segHdr)
                mask.write((byte) 0xff);

            mask.write(securityHdrAuthMask);

            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}