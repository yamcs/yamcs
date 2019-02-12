package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class FileStoreRequest {

    public static byte TYPE = 0x00;

    private ActionCode actionCode;
    private LV firstFileName;
    private LV secondFileName;

    public FileStoreRequest(ActionCode actionCode, LV firstFileName, LV secondFileName) {
        this.actionCode = actionCode;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
    }

    public FileStoreRequest(ActionCode actionCode, LV firstFileName) {
        this(actionCode, firstFileName, null);
    }

    public ActionCode getActionCode() {
        return this.actionCode;
    }

    public LV getFirstFileName() {
        return this.firstFileName;
    }

    public LV getSecondFileName() {
        return this.secondFileName;
    }

    // TODO merge this with fromTLV
    private static FileStoreRequest readFileStoreRequest(ByteBuffer buffer) {
        ActionCode c = ActionCode.readActionCode(buffer);
        return (c.hasSecondFileName()
                ? new FileStoreRequest(c, LV.readLV(buffer), LV.readLV(buffer))
                : new FileStoreRequest(c, LV.readLV(buffer)));
    }

    public static FileStoreRequest fromTLV(TLV tlv) {
        return readFileStoreRequest(ByteBuffer.wrap(tlv.getValue()));
    }

    public TLV toTLV() {
        return new TLV(FileStoreRequest.TYPE,
                ByteBuffer
                        .allocate(1
                                + firstFileName.getValue().length
                                + secondFileName.getValue().length)
                        .put((byte) (actionCode.getCode() << 4))
                        .put(firstFileName.getValue())
                        .put(secondFileName.getValue())
                        .array());

    }
}
