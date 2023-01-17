package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;

import java.nio.ByteBuffer;

public class DirectoryListingRequest extends ReservedMessageToUser {

    private final String directoryName;
    private final String directoryFileName; // Where the directory listing will be saved locally

    public DirectoryListingRequest(String directoryName, String directoryFileName) {
        super(MessageType.DIRECTORY_LISTING_REQUEST, encode(directoryName, directoryFileName));
        this.directoryName = directoryName;
        this.directoryFileName = directoryFileName;
    }

    public DirectoryListingRequest(byte[] content) {
        super(MessageType.DIRECTORY_LISTING_REQUEST, content);

        ByteBuffer buffer = ByteBuffer.wrap(content);
        this.directoryName = new String(LV.readLV(buffer).getValue());
        this.directoryFileName = new String(LV.readLV(buffer).getValue());
    }

    private static byte[] encode(String directoryName, String directoryFileName) {
        return Bytes.concat(
                new LV(directoryName).getBytes(),
                new LV(directoryFileName).getBytes()
            );
    }


    public String getDirectoryName() {
        return directoryName;
    }

    public String getDirectoryFileName() {
        return directoryFileName;
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.DIRECTORY_LISTING_REQUEST
                + ", directoryName=" + directoryName + ", directoryFileName=" + directoryFileName + "}";
    }


    @Override
    public String toString() {
        return "DirectoryListingRequest(directoryName: " + directoryName + ", directoryFileName:" + directoryFileName + ")";
    }

}
