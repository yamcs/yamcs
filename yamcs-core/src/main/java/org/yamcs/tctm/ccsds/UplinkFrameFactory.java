package org.yamcs.tctm.ccsds;

public interface UplinkFrameFactory<T extends UplinkTransferFrame> {
    public T makeCtrlFrame(int dataLength);

    public T makeDataFrame(int dataLength, long generationTime);

    public T makeDataFrame(int dataLength, long generationTime, byte mapId);

    /**
     * retrieves the headers size + CRC size
     */
    public int getFramingLength(int vcId);

    public byte[] encodeFrame(T ttf);

}
