package org.yamcs.xtce;

import org.yamcs.xtce.util.HexUtils;

/**
 * 
 * Holds an optional attributes name, bitOrder, byteOrderList, required attributes binaryValue, sizeInBits and optional
 * LocationInContainerInBits, RepeatEntry and IncludeCondition.
 * 
 */
public class FixedValueEntry extends SequenceEntry {
    private static final long serialVersionUID = 1L;

    // An optional name, this name cannot be NameReferenced and is only a form of documentation.
    final String name;

    /**
     * The fixed/constant value that should be encoded into the sequence. This value provided should have sufficient bit
     * length to accommodate the size in bits. If the value is larger, the most significant unnecessary bits are
     * dropped. The value provided should be in network byte order for encoding.
     **/
    final byte[] binaryValue;

    // the size in bits of the value - this should not be more than the length in bits of the binaryValue
    final int sizeInBits;

    public FixedValueEntry(String name, byte[] binaryValue, int sizeInBits) {
        if (sizeInBits > binaryValue.length * 8) {
            throw new IllegalArgumentException("binaryValue has to have at least sizeInBits(" + sizeInBits
                    + ") bits, instead of " + (binaryValue.length * 8));
        }
        this.name = name;
        this.binaryValue = binaryValue;
        this.sizeInBits = sizeInBits;
    }

    public FixedValueEntry(int locationInContainerInBits, ReferenceLocationType location, String name,
            byte[] binaryValue, int sizeInBits) {
        super(locationInContainerInBits, location);
        if (sizeInBits > binaryValue.length * 8) {
            throw new IllegalArgumentException("binaryValue has to have at least sizeInBits(" + sizeInBits
                    + ") bits, instead of " + (binaryValue.length * 8));
        }
        this.name = name;
        this.binaryValue = binaryValue;
        this.sizeInBits = sizeInBits;
    }

    public String getName() {
        return name;
    }

    public byte[] getBinaryValue() {
        return binaryValue;
    }

    public int getSizeInBits() {
        return sizeInBits;
    }

    @Override
    public String toString() {
        return "FixedValueEntry position:" + getIndex() + ", container:" + container.getName() +
                " locationInContainer:" + getLocationInContainerInBits() + " from:" + getReferenceLocation() +
                ", sizeInBits: " + sizeInBits +
                ", binaryValue: " + HexUtils.hex(binaryValue);
    }
}
