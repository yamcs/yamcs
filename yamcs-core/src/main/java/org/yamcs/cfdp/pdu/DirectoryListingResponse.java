package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;

import java.nio.ByteBuffer;

public class DirectoryListingResponse extends ReservedMessageToUser {

    private final ListingResponseCode listingResponseCode;
    private final String directoryName;
    private final String directoryFileName; // Where the directory listing will be saved locally

    public enum ListingResponseCode {
        SUCCESSFUL(0),
        UNSUCCESSFUL(1);

        private final int value;
        ListingResponseCode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ListingResponseCode fromValue(int value) {
            switch (value) {
                case 0: return SUCCESSFUL;
                case 1: return UNSUCCESSFUL;
                default: return null;
            }
        }
    }

    public DirectoryListingResponse(ListingResponseCode listingResponseCode, String directoryName, String directoryFileName) {
        super(MessageType.DIRECTORY_LISTING_RESPONSE, encode(listingResponseCode, directoryName, directoryFileName));
        this.listingResponseCode = listingResponseCode;
        this.directoryName = directoryName;
        this.directoryFileName = directoryFileName;
    }

    public DirectoryListingResponse(byte[] content) {
        super(MessageType.DIRECTORY_LISTING_RESPONSE, content);

        ByteBuffer buffer = ByteBuffer.wrap(content);
        this.listingResponseCode = ListingResponseCode.fromValue(buffer.get() >> 7);
        this.directoryName = new String(LV.readLV(buffer).getValue());
        this.directoryFileName = new String(LV.readLV(buffer).getValue());
    }

    private static byte[] encode(ListingResponseCode listingResponseCode, String directoryName, String directoryFileName) {
        return Bytes.concat(
                new byte[] { CfdpUtils.boolToByte(listingResponseCode.getValue() == 1, 0) },
                new LV(directoryName).getBytes(),
                new LV(directoryFileName).getBytes()
        );
    }

    public ListingResponseCode getListingResponseCode() {
        return listingResponseCode;
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.DIRECTORY_LISTING_RESPONSE
                + ", listingResponseCode=" + listingResponseCode + ", directoryName=" + directoryName + ", directoryFileName=" + directoryFileName + "}";
    }


    @Override
    public String toString() {
        return "DirectoryListingResponse(listingResponseCode: " + listingResponseCode + ", directoryName: " + directoryName + ", directoryFileName:" + directoryFileName + ")";
    }

}
