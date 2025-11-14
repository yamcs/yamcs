package org.yamcs.security.sdls;

/**
 * Sequence number used for initialisation vector.
 * <p>
 * SDLS specifies that it can be between 1 and 32 octets.
 */
public final class IvSeqNum {
    private final int[] words; // little-endian: words[0] = least significant 32 bits
    private final int octets; // number of octets (1–32)

    public IvSeqNum(int octets) {
        if (octets < 1 || octets > 32)
            throw new IllegalArgumentException("Octets must be 1–32");
        this.octets = octets;
        this.words = new int[(octets + 3) / 4]; // ceil(octets/4)
    }

    /**
     * Create an IvSeqNum from a big endian byte array. Pads or truncates to exactly octets bytes.
     * 
     * @param bytes
     *            input array
     * @param octets
     *            desired counter length in bytes (1–32)
     * @return IvSeqNum instance
     */
    public static IvSeqNum fromBytes(byte[] bytes, int octets) {
        if (octets < 1 || octets > 32)
            throw new IllegalArgumentException("Octets must be 1–32");

        IvSeqNum iv = new IvSeqNum(octets);

        int srcPos = Math.max(bytes.length - octets, 0);
        int length = Math.min(bytes.length, octets);

        // copy last 'length' bytes into words[], LSB first
        for (int i = 0; i < length; i++) {
            int b = bytes[srcPos + length - 1 - i] & 0xFF; // LSB first
            int wordIndex = i / 4;
            int shift = (i % 4) * 8;
            iv.words[wordIndex] |= b << shift;
        }

        return iv;
    }

    /**
     * Convert this IvSeqNum to a big-endian byte array.
     * 
     * @param outOctets
     *            desired number of bytes in the output (1–32)
     * @return byte array, MSB first
     */
    public byte[] toBytes(int outOctets) {
        if (outOctets < this.octets || outOctets > 32) {
            throw new IllegalArgumentException(
                    "Output length must be at least the IvSeqNum length (" + this.octets + ") and at most 32");
        }

        byte[] arr = new byte[outOctets];
        int padding = outOctets - this.octets; // leading zeros if any

        for (int i = 0; i < this.octets; i++) {
            int srcIndex = i;
            int wordIndex = srcIndex / 4;
            int shift = (srcIndex % 4) * 8;
            arr[padding + this.octets - 1 - i] = (byte) ((words[wordIndex] >>> shift) & 0xFF);
        }

        return arr;
    }

    // increment by 1 with natural wraparound
    public void increment() {
        long carry = 1;
        for (int i = 0; i < words.length && carry != 0; i++) {
            long sum = (words[i] & 0xFFFFFFFFL) + carry;
            words[i] = (int) sum;
            carry = sum >>> 32;
        }
    }

    /**
     * Verify that the receivedSeq is in the (this, this+windowSize] taking into account any wrap around
     * 
     * @param receivedSeq
     * @param windowSize
     * @return
     */
    public boolean verifyInWindow(IvSeqNum receivedSeq, int windowSize) {
        if (words.length != receivedSeq.words.length || octets != receivedSeq.octets)
            throw new IllegalArgumentException("Mismatched sizes");

        // fast path for +1
        if (isNext(receivedSeq)) {
            return true;
        }

        IvSeqNum diff = subtractMod(receivedSeq, this);
        if (diff.isZero()) {
            return false;
        }

        return diff.lessOrEqual(windowSize);
    }

    // fast check if receivedSeq == this + 1
    private boolean isNext(IvSeqNum receivedSeq) {
        long carry = 1;
        for (int i = 0; i < words.length && carry != 0; i++) {
            long expected = (words[i] & 0xFFFFFFFFL) + carry;
            if ((receivedSeq.words[i] & 0xFFFFFFFFL) != (expected & 0xFFFFFFFFL))
                return false;
            carry = expected >>> 32;
        }
        // any remaining words in other must match this
        for (int i = 0; i < words.length; i++) {
            if ((receivedSeq.words[i] & 0xFFFFFFFFL) != (words[i] & 0xFFFFFFFFL) + ((i == 0) ? 1 : 0))
                break;
        }
        return true;
    }



    private boolean isZero() {
        for (int w : words)
            if (w != 0)
                return false;
        return true;
    }

    // check if counter value <= int windowSize (only uses low 32 bits)
    private boolean lessOrEqual(int value) {
        // higher words must be zero
        for (int i = words.length - 1; i > 0; i--) {
            if (words[i] != 0)
                return false;
        }
        long low = words[0] & 0xFFFFFFFFL;
        return low <= (value & 0xFFFFFFFFL);
    }

    private static IvSeqNum subtractMod(IvSeqNum a, IvSeqNum b) {
        IvSeqNum res = new IvSeqNum(a.octets);
        long borrow = 0;
        for (int i = 0; i < a.words.length; i++) {
            long diff = (a.words[i] & 0xFFFFFFFFL) - (b.words[i] & 0xFFFFFFFFL) - borrow;
            if (diff < 0) {
                diff += 1L << 32;
                borrow = 1;
            } else {
                borrow = 0;
            }
            res.words[i] = (int) diff;
        }
        return res;
    }

    protected IvSeqNum clone() {
        IvSeqNum c = new IvSeqNum(this.octets);
        System.arraycopy(this.words, 0, c.words, 0, words.length);
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IvSeqNum))
            return false;
        IvSeqNum other = (IvSeqNum) o;
        if (octets != other.octets)
            return false;
        for (int i = 0; i < words.length; i++) {
            if (words[i] != other.words[i])
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(octets * 2);
        for (int i = octets - 1; i >= 0; i--) { // MSB first
            int byteIndex = i;
            int wordIndex = byteIndex / 4;
            int shift = (byteIndex % 4) * 8;
            int b = (words[wordIndex] >>> shift) & 0xFF;
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}