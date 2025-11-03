package org.yamcs.security.sdls;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class StandardAuthMask {
    public static byte[] USLP(int priHdrLen, int insZoneLen) {
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

            // Authenticate SPI
            mask.write(new byte[]{(byte) 0xff, (byte) 0xff});

            // Don't authenticate IV
            for (int i = 0; i < SdlsSecurityAssociation.GCM_IV_LEN_BYTES; ++i)
                mask.write(0);
            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] TM(int secHdrLen) {
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

            // Authenticate SPI
            mask.write(new byte[]{(byte) 0xff, (byte) 0xff});

            // Don't authenticate IV
            for (int i = 0; i < SdlsSecurityAssociation.GCM_IV_LEN_BYTES; ++i)
                mask.write(0);
            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] AOS(boolean frameHdrErrCtrl, int insZoneLen) {
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

            // Authenticate SPI
            mask.write(new byte[]{(byte) 0xff, (byte) 0xff});
            // Don't authenticate IV
            for (int i = 0; i < SdlsSecurityAssociation.GCM_IV_LEN_BYTES; ++i)
                mask.write(0);
            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] TC(boolean segHdr) {
        try {
            ByteArrayOutputStream mask = new ByteArrayOutputStream();
            mask.write(new byte[]{
                    0, 0,
                    // VCID
                    (byte) 0b1111_1100, 0, 0,
            });
            if (segHdr)
                mask.write((byte) 0xff);

            // SPI
            mask.write(new byte[]{(byte) 0xff, (byte) 0xff});

            // IV
            for (int i = 0; i < SdlsSecurityAssociation.GCM_IV_LEN_BYTES; ++i)
                mask.write(0);

            return mask.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}