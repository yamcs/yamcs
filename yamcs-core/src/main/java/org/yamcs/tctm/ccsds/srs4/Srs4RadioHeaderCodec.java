package org.yamcs.tctm.ccsds.srs4;

import org.yamcs.tctm.TcTmException;
import org.yamcs.utils.ByteArrayUtils;

final class Srs4RadioHeaderCodec {
    static final int HEADER_LENGTH = 4;
    static final int MAX_CONTENT_LENGTH = 0x7FF;
    static final int SPACECRAFT_ID_LENGTH = 2;

    record DecodedRadioFrame(Srs4Flow flow, byte[] data, int offset, int length) {
    }

    private final int spacecraftId;

    Srs4RadioHeaderCodec(int spacecraftId) {
        this.spacecraftId = spacecraftId;
    }

    byte[] encode(Srs4Flow flow, byte[] payload) {
        int contentLength = SPACECRAFT_ID_LENGTH + payload.length;
        if (contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("SRS4 radio content length " + contentLength + " exceeds 11 bits");
        }
        byte[] result = new byte[HEADER_LENGTH + payload.length];
        int typeAndLength = (flow == Srs4Flow.ETHERNET ? 1 << 11 : 0) | contentLength;
        ByteArrayUtils.encodeUnsignedShort(typeAndLength, result, 0);
        ByteArrayUtils.encodeUnsignedShort(spacecraftId, result, SPACECRAFT_ID_LENGTH);
        System.arraycopy(payload, 0, result, HEADER_LENGTH, payload.length);
        return result;
    }

    DecodedRadioFrame decode(byte[] data, int offset, int length) throws TcTmException {
        if (length < HEADER_LENGTH) {
            throw new TcTmException("SRS4 radio frame is shorter than 4 bytes");
        }
        int word = ByteArrayUtils.decodeUnsignedShort(data, offset);
        if ((word & 0xF000) != 0) {
            throw new TcTmException("SRS4 radio reserved bits are not zero");
        }
        int declaredLength = word & MAX_CONTENT_LENGTH;
        if (declaredLength != length - SPACECRAFT_ID_LENGTH) {
            throw new TcTmException("SRS4 radio length is " + declaredLength + ", received " + (length - SPACECRAFT_ID_LENGTH));
        }
        int receivedSpacecraftId = ByteArrayUtils.decodeUnsignedShort(data, offset + SPACECRAFT_ID_LENGTH);
        if (receivedSpacecraftId != spacecraftId) {
            throw new TcTmException("Unexpected SRS4 radio spacecraft ID " + receivedSpacecraftId
                    + " (expected " + spacecraftId + ")");
        }
        Srs4Flow flow = (word & (1 << 11)) == 0 ? Srs4Flow.CAN : Srs4Flow.ETHERNET;
        return new DecodedRadioFrame(flow, data, offset + HEADER_LENGTH, length - HEADER_LENGTH);
    }
}
