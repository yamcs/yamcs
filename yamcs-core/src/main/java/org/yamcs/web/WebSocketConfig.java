package org.yamcs.web;

import io.netty.channel.WriteBufferWaterMark;

public class WebSocketConfig {

    private int maxFrameLength = 65535;
    private WriteBufferWaterMark writeBufferWaterMark = new WriteBufferWaterMark(32 * 1024, 64 * 1024);
    private int connectionCloseNumDroppedMsg = 5;

    /**
     * Returns the water mark for the write buffer. The higher the values, the more memory it might consume but it will
     * be more resilient against unstable networks
     */
    public WriteBufferWaterMark getWriteBufferWaterMark() {
        return writeBufferWaterMark;
    }

    public void setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        this.writeBufferWaterMark = writeBufferWaterMark;
    }

    /**
     * Returns the maximum number of dropped messages after which to close the connection
     */
    public int getConnectionCloseNumDroppedMsg() {
        return connectionCloseNumDroppedMsg;
    }

    public void setConnectionCloseNumDroppedMsg(int connectionCloseNumDroppedMsg) {
        this.connectionCloseNumDroppedMsg = connectionCloseNumDroppedMsg;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public void setMaxFrameLength(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }
}
