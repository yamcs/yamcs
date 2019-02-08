package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class FileStoreRequest {
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

    private static FileStoreRequest readFileStoreRequest(ByteBuffer buffer) {
        ActionCode c = ActionCode.readActionCode(buffer);
        return (c.hasSecondFileName()
                ? new FileStoreRequest(c, LV.readLV(buffer), LV.readLV(buffer))
                : new FileStoreRequest(c, LV.readLV(buffer)));
    }

    public static FileStoreRequest fromTLV(TLV tlv) {
        return readFileStoreRequest(ByteBuffer.wrap(tlv.getValue()));
    }
}
