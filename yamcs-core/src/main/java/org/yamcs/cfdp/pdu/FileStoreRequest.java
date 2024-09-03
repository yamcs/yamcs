package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.utils.StringConverter;

import com.google.common.primitives.Bytes;

/**
 * NOT IMPLEMENTED
 */
public class FileStoreRequest extends TLV {
    //Action Code (4bits)
    //Spare (4bits)
    //First File Name (LV)
    //Second File Name (LV) (Only present for some action codes.)

    //Action Code Action Second File Name Present
    //‘0000’ Create File N
    //‘0001’ Delete File N
    //‘0010’ Rename File Y
    //‘0011’ Append File Y
    //‘0100’ Replace File Y
    //‘0101’ Create Directory N
    //‘0110’ Remove Directory N
    //‘0111’ Deny File (delete if present) N
    //‘1000’ Deny Directory (remove if present) N

    // Set spare to 0 for now
    final static byte spare = 0;

    public enum FilestoreType {
        CREATE((byte) 0x00),
        DELETE((byte) 0x01),
        RENAME((byte) 0x02),
        APPEND((byte) 0x03),
        REPLACE((byte) 0x04),
        CREATE_DIRECTORY((byte) 0x05),
        REMOVE_DIRECTORY((byte) 0x06),
        DENY_FILE((byte) 0x07),
        DENY_DIRECTORY((byte) 0x08),
        COMPRESS((byte) 0x09),          // These are Pixxel additions
        UNCOMPRESS((byte) 0x0A),        // These are Pixxel additions
        VERIFY_CHECKSUM((byte) 0x0B),   // These are Pixxel additions
        UPDATE_XDI((byte) 0x0C);        // These are Pixxel additions

        private final byte bytes;

        FilestoreType(byte bytes) {
            this.bytes = bytes;
        }

        public byte getByte() {
            return bytes;
        }

        public static FilestoreType fromByte(byte b) {
            switch (b) {
                case 0x00: return CREATE;
                case 0x01: return DELETE;
                case 0x02: return RENAME;
                case 0x03: return APPEND;
                case 0x04: return REPLACE;
                case 0x05: return CREATE_DIRECTORY;
                case 0x06: return REMOVE_DIRECTORY;
                case 0x07: return DENY_FILE;
                case 0x08: return DENY_DIRECTORY;
                case 0x09: return COMPRESS;
                case 0x0A: return UNCOMPRESS;
                case 0x0B: return VERIFY_CHECKSUM;
                case 0x0C: return UPDATE_XDI;
                default: return CREATE; // FIXME: What do be done here?
            }
        }
    }

    FilestoreType ft;
    LV firstFileName;
    LV secondFileName;

    public FileStoreRequest(FilestoreType ft, LV firstFileName, LV secondFileName) {
        super(TLV.TYPE_FILE_STORE_REQUEST, encode(ft, firstFileName, secondFileName));

        this.ft = ft;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
    }

    public static byte[] encode(FilestoreType ft, LV firstFileName, LV secondFileName) {
        if (secondFileName == null)
            return Bytes.concat(ByteBuffer.allocate(1).put((byte) ((ft.getByte() << 4 & 0xF0) | (spare & 0x0F))).array(), firstFileName.getBytes());
        
        return Bytes.concat(ByteBuffer.allocate(1).put((byte) ((ft.getByte() << 4 & 0xF0) | (spare & 0x0F))).array(), firstFileName.getBytes(), secondFileName.getBytes());
    }

    @Override
    public String toString() {
        return "FSR [type=" + ft.name() + ", f1=" + firstFileName + ", f2=" + secondFileName + "]\n";
    }
}
