package org.yamcs.tctm;

public class TmContainer {
    private byte[] containerPayload;

    public TmContainer(byte[] containerPayload) {
        this.containerPayload = containerPayload;
    }

    public byte[] getContainerPayload() {
        return this.containerPayload;
    }
}
