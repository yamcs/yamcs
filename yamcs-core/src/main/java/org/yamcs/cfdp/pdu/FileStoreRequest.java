package org.yamcs.cfdp.pdu;

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
    public FileStoreRequest() {
        super(TLV.TYPE_FILE_STORE_REQUEST, encode());
    }

    private static byte[] encode() {
        return new byte[0];
    }
}
