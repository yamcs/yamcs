package org.yamcs.commanding;

import org.yamcs.cmdhistory.protobuf.Cmdhistory;

/**
 * Location of an argument value inside the binary of an encoded command.
 * <p>
 * The locations are recorded by the command encoder for each top level argument as well as for each member of an
 * aggregate and each element of an array. They are relative to the start of the binary as produced by the encoder
 * (i.e. before any change made by the command post-processor).
 *
 * @param path
 *            the argument name, followed for members and elements by the path inside the value, for example
 *            {@code x}, {@code activities}, {@code activities[1]} or {@code activities[1].tc}
 * @param bitPosition
 *            position in bits of the start of the encoded value (including any leading size tag)
 * @param bitSize
 *            number of bits used to encode the value
 */
public record ArgumentLocation(String path, int bitPosition, int bitSize) {

    public boolean isByteAligned() {
        return (bitPosition & 7) == 0 && (bitSize & 7) == 0;
    }

    /**
     * @return the byte offset of the value; meaningful only if the value is byte aligned
     */
    public int byteOffset() {
        return bitPosition >>> 3;
    }

    /**
     * @return the size of the value in bytes; meaningful only if the value is byte aligned
     */
    public int byteLength() {
        return bitSize >>> 3;
    }

    public Cmdhistory.ArgumentLocation toProto() {
        return Cmdhistory.ArgumentLocation.newBuilder().setPath(path).setBitPosition(bitPosition)
                .setBitSize(bitSize).build();
    }

    public static ArgumentLocation fromProto(Cmdhistory.ArgumentLocation loc) {
        return new ArgumentLocation(loc.getPath(), loc.getBitPosition(), loc.getBitSize());
    }
}
