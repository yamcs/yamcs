package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class FileStoreResponse {

    public static byte TYPE = 0x01;

    private ActionCode actionCode;
    private StatusCode statusCode;
    private LV firstFileName;
    private LV secondFileName;
    private LV filestoreMessage;

    public FileStoreResponse(ActionCode actionCode, StatusCode statusCode, LV firstFileName, LV secondFileName,
            LV filestoreMessage) {
        this.actionCode = actionCode;
        this.statusCode = statusCode;
        this.firstFileName = firstFileName;
        this.secondFileName = secondFileName;
        this.filestoreMessage = filestoreMessage;
    }

    public FileStoreResponse(ActionCode actionCode, StatusCode statusCode, LV firstFileName, LV filestoreMessage) {
        this(actionCode, statusCode, firstFileName, null, filestoreMessage);
    }

    public ActionCode getActionCode() {
        return this.actionCode;
    }

    public StatusCode getStatusCode() {
        return this.statusCode;
    }

    public LV getFirstFileName() {
        return this.firstFileName;
    }

    public LV getSecondFileName() {
        return this.secondFileName;
    }

    public LV getFilestoreMessage() {
        return this.filestoreMessage;
    }

    // TODO, merge this with fromTLV
    private static FileStoreResponse readFileStoreResponse(ByteBuffer buffer) {
        byte b = buffer.get();
        ActionCode c = ActionCode.readActionCode(b);
        StatusCode s = StatusCode.readStatusCode(b);
        return (c.hasSecondFileName()
                ? new FileStoreResponse(c, s, LV.readLV(buffer), LV.readLV(buffer), LV.readLV(buffer))
                : new FileStoreResponse(c, s, LV.readLV(buffer), LV.readLV(buffer)));
    }

    public static FileStoreResponse fromTLV(TLV tlv) {
        return readFileStoreResponse(ByteBuffer.wrap(tlv.getValue()));
    }

    public TLV toTLV() {
        return new TLV(FileStoreResponse.TYPE,
                ByteBuffer
                        .allocate(1
                                + firstFileName.getValue().length
                                + secondFileName.getValue().length
                                + filestoreMessage.getValue().length)
                        .put((byte) ((actionCode.getCode() << 4)
                                | statusCode.getCode()))
                        .put(firstFileName.getValue())
                        .put(secondFileName.getValue())
                        .put(filestoreMessage.getValue())
                        .array());

    }

}
