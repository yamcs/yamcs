package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

import org.yamcs.utils.BitBuffer;

/** Shared helpers for PUS application/source-data fields that are not octet aligned. */
final class PusPackedFields {
    // APID width used inside PUS service request identifiers, not the CCSDS header.
    static final int APID_BITS = 11;
    // Sequence-count width used inside PUS service request identifiers, not the CCSDS header.
    static final int SEQCOUNT_BITS = 14;

    static final int OCTET_BITS = 8;
    static final int STRUCTURE_ID_BITS = 8;
    static final int EVENT_DEFINITION_ID_BITS = 8;
    static final int MAX_OCTET_COUNT = 0xFF;

    private PusPackedFields() {
    }

    static BitBuffer bitBuffer(ByteBuffer byteBuffer) {
        return new BitBuffer(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position());
    }

    static int readUnsigned(BitBuffer bits, int bitCount) {
        return (int) bits.getBits(bitCount);
    }

    static int bytesForBits(long bitCount) {
        if (bitCount < 0 || bitCount > (long) Integer.MAX_VALUE * OCTET_BITS) {
            throw new IllegalArgumentException("Invalid bit count " + bitCount);
        }
        return (int) ((bitCount + OCTET_BITS - 1) / OCTET_BITS);
    }

    static void putBytes(BitBuffer bits, byte[] bytes) {
        for (byte value : bytes) {
            bits.putBits(value & 0xFF, OCTET_BITS);
        }
    }
}
