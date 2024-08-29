package org.yamcs.cfdp.pdu;

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

    // PS: This include the spare field as expected by the standard
    public enum FilestoreType {
        CREATE((byte) 0x00),
        DELETE((byte) 0x10),
        RENAME((byte) 0x20),
        APPEND((byte) 0x30),
        REPLACE((byte) 0x40),
        CREATE_DIRECTORY((byte) 0x50),
        REMOVE_DIRECTORY((byte) 0x60),
        DENY_FILE((byte) 0x70),
        DENY_DIRECTORY((byte) 0x80),
        DEFAULT((byte) 0x90);

        private final byte[] bytes;

        FilestoreType(byte bytes) {
            this.bytes = new byte[] {bytes};
        }

        public byte[] getBytes() {
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
                default: return DEFAULT; // FIXME: What do be done here?
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
            return Bytes.concat(ft.getBytes(), firstFileName.getBytes());
        
        return Bytes.concat(ft.getBytes(), firstFileName.getBytes(), secondFileName.getBytes());
    }

    @Override
    public String toString() {
        return "FSR [type=" + ft.name() + ", f1=" + firstFileName + ", f2=" + secondFileName + "]\n";
    }
}
