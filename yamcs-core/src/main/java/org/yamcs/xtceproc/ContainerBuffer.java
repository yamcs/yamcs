package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.yamcs.xtce.IntegerDataEncoding;

/**
 * used during processing a container (packet) to keep track on the position of the processing
 * 
 * The ByteBuffer is used to access the data, whereas the bitPosition is a position in bits inside the ByteBuffer.
 * 
 * The ByteBuffer attributes (position, mark, etc) are not used
 * 
 * 
 * @author nm
 *
 */
public class ContainerBuffer {
    final ByteBuffer bb;
    int bitPosition;
    public ContainerBuffer(ByteBuffer bb, int bitPosition) {
        this.bb = bb;
        this.bitPosition = bitPosition;
    }

    public int getBitPosition() {
        return bitPosition;
    }

    public void setBitPosition(int bitPosition) {
        this.bitPosition = bitPosition;
    }

    public long getBits(int numBits, ByteOrder order, IntegerDataEncoding.Encoding encoding) {

        int byteOffset = bitPosition/8;
        int byteSize = (bitPosition + numBits-1)/8-byteOffset+1;

        int bitOffsetInsideMask = bitPosition - 8*byteOffset;
        int bitsToShift = 8*byteSize-bitOffsetInsideMask - numBits;
        long mask = (-1L<<(64-numBits))>>>(64-numBits-bitsToShift);
        bb.order(order);

        long rv=0;
        switch(byteSize) {
            case 1:
                rv = bb.get(byteOffset);
                break;
            case 2:
                rv = bb.getShort(byteOffset);
                break;
            case 3:
                if(order == ByteOrder.BIG_ENDIAN) {
                    rv = (bb.getShort(byteOffset) & 0xFFFF) << 8;
                    rv += bb.get(byteOffset + 2) & 0xFF;
                } else {
                    rv = bb.getShort(byteOffset) & 0xFFFF;
                    rv += (bb.get(byteOffset + 2) & 0xFF) << 16;
                }
                break;
            case 4:
                rv = bb.getInt(byteOffset);
                break;
            case 5:
                if(order == ByteOrder.BIG_ENDIAN) {
                    rv = (bb.getInt(byteOffset) & 0xFFFFFFFFL) << 8;
                    rv += bb.get(byteOffset + 4) & 0xFF;
                } else {
                    rv = bb.getInt(byteOffset) & 0xFFFFFFFFL;
                    rv += (bb.get(byteOffset + 4) & 0xFFL) << 32;
                }
                break;
            case 6:
                if (order == ByteOrder.BIG_ENDIAN) {
                rv = (bb.getInt(byteOffset) & 0xFFFFFFFFL) << 16;
                rv += bb.getShort(byteOffset + 4) & 0xFFFF;
            } else {
                rv = bb.getInt(byteOffset) & 0xFFFFFFFFL;
                rv += bb.getShort(byteOffset + 4) & 0xFFFFL << 32;
            }
                break;
            case 7:
                if (order == ByteOrder.BIG_ENDIAN) {
                    rv = (bb.getInt(byteOffset) & 0xFFFFFFFFL) << 24;
                    rv += (bb.getShort(byteOffset + 4) & 0xFFFFL) << 8;
                    rv += bb.get(byteOffset + 6) & 0xFFL;
                } else {
                    rv = bb.getInt(byteOffset) & 0xFFFFFFFFL;
                    rv += (bb.getShort(byteOffset + 4) & 0xFFFFL) << 32;
                    rv += (bb.get(byteOffset + 6) & 0xFFL) << 48;
                }
                break;
            case 8:
                rv = bb.getLong(byteOffset);
                break;
            default:
                throw new UnsupportedOperationException("parameter extraction for "+byteSize+"not supported");
        }
        bitPosition+=numBits;

        switch(encoding) {
        case TWOS_COMPLEMENT:
            //we shift it to the left first such that the sign bit arrives on the first position
            rv = (rv&mask)<<(64-numBits-bitsToShift);
            rv = rv>>(64-numBits);
            break;
        case UNSIGNED:
            //we use the ">>>" such that the sign bit is not carried
            rv = (rv&mask)>>>bitsToShift;
            break;
        case SIGN_MAGNITUDE:
            boolean negative = ((rv>>>(numBits-1) & 1L) == 1L);
            mask >>>= 1; // Don't include sign in mask
            rv=(rv&(mask))>>>bitsToShift;
            if (negative) {
                rv = -rv;
            }
            break;
            default:
                throw new UnsupportedOperationException("encoding "+encoding+" not implemented");
        }
        return rv;
    }
}