package org.yamcs.cfdp;

public class DataFileSegment {

    private long offset;
    private byte[] data;

    public DataFileSegment(long offset, byte[] data) {
        this.offset = offset;
        this.data = data;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return data.length;
    }

    public byte[] getData() {
        return data;
    }

}
