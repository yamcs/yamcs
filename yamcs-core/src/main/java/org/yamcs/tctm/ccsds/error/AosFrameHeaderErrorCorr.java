package org.yamcs.tctm.ccsds.error;

import org.yamcs.rs.ReedSolomon;
import org.yamcs.rs.ReedSolomonException;

/**
 * Reed-Solomon (10, 6) encoding/decoding as specified in
 * CCSDS RECOMMENDED STANDARD FOR AOS SPACE DATA LINK PROTOCOL
 * 
 * CCSDS 732.0-B-3 September 2015
 * 4.1.2.6 Frame Header Error Control
 * 
 * @author nm
 *
 */
public class AosFrameHeaderErrorCorr {
    static ReedSolomon rs = new ReedSolomon(4, 4, 6, 1, 0x13, 5);

    /**
     * Compute the Error Control word based on the virtual channel id and signaling field byte
     * 
     * @param gvcid
     *            10-bit Master Channel Identifier followed by the Virtual Channel Identifier in the lowest 16 bits of
     *            the variable
     * @param signalingField
     *            - 8 bit signalling byte in the lowest 8 bits of the variable
     * @return
     */
    public static int encode(int gvcid, int signalingField) {
        byte[] data = new byte[] {
                (byte) ((gvcid >> 12) & 0xF),
                (byte) ((gvcid >> 8) & 0xF),
                (byte) ((gvcid >> 4) & 0xF),
                (byte) ((gvcid) & 0xF),
                (byte) ((signalingField >> 4) & 0xF),
                (byte) ((signalingField) & 0xF) };

        byte[] parity = new byte[4];

        rs.encode(data, parity);
        return (parity[0] << 12) + (parity[1] << 8) + (parity[2] << 4) + (parity[3]);
    }

    /**
     * Based on the received header information, reconstitutes the correct header if possible
     * 
     * @param gvcid
     *            10-bit Master Channel Identifier followed by the Virtual Channel Identifier
     * @param signalingField
     *            8 bit signalling byte
     * @param errControl
     *            16 bits error control
     * @return
     * @throws ReedSolomonException
     *             thrown if data cannot be decoded/corrected
     */
    public static DecoderResult decode(int gvcid, int signalingField, int errControl) throws ReedSolomonException {
        byte[] data = new byte[] {
                (byte) ((gvcid >> 12) & 0xF),
                (byte) ((gvcid >> 8) & 0xF),
                (byte) ((gvcid >> 4) & 0xF),
                (byte) ((gvcid) & 0xF),
                (byte) ((signalingField >> 4) & 0xF),
                (byte) ((signalingField) & 0xF),
                (byte) ((errControl >> 12) & 0xF),
                (byte) ((errControl >> 8) & 0xF),
                (byte) ((errControl >> 4) & 0xF),
                (byte) ((errControl & 0xF))
        };

        int numc = rs.decode(data, null);
        int cvcid = (data[0] << 12) + (data[1] << 8) + (data[2] << 4) + data[3];
        int csig = (data[4] << 4) + data[5];
        return new DecoderResult(cvcid, csig, numc);

    }

    public static class DecoderResult {
        public final int gvcid;
        public final int signalingField;
        public final int numErrorsCorrected;

        public DecoderResult(int gvcid, int signalingField, int numErrs) {
            super();
            this.gvcid = gvcid;
            this.signalingField = signalingField;
            this.numErrorsCorrected = numErrs;
        }

    }

}
