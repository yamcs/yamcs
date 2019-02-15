package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MetadataPacket extends CfdpPacket {

    private boolean segmentationControl;
    private long fileSize;
    private LV sourceFileName;
    private LV destinationFileName;
    private List<FileStoreRequest> filestoreRequests = new ArrayList<FileStoreRequest>();
    private List<MessageToUser> messagesToUser = new ArrayList<MessageToUser>();
    private List<FaultHandlerOverride> faultHandlerOverrides = new ArrayList<FaultHandlerOverride>();
    private TLV flowLabel;

    public MetadataPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        byte temp = buffer.get();
        this.segmentationControl = (temp & 0x01) == 1;
        this.fileSize = Utils.getUnsignedInt(buffer);
        this.sourceFileName = LV.readLV(buffer);
        this.destinationFileName = LV.readLV(buffer);

        while (buffer.hasRemaining()) {
            TLV tempTLV = TLV.readTLV(buffer);
            switch (tempTLV.getType()) {
            case 0:
                filestoreRequests.add(FileStoreRequest.fromTLV(tempTLV));
                break;
            case 2:
                messagesToUser.add(MessageToUser.fromTLV(tempTLV));
                break;
            case 4:
                faultHandlerOverrides.add(FaultHandlerOverride.fromTLV(tempTLV));
                break;
            case 5:
                flowLabel = tempTLV;
                break;
            }
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put((byte) ((segmentationControl ? 1 : 0) << 7));
        Utils.writeUnsignedInt(buffer, fileSize);
        sourceFileName.writeToBuffer(buffer);
        destinationFileName.writeToBuffer(buffer);
        filestoreRequests.forEach(x -> x.toTLV().writeToBuffer(buffer));
        messagesToUser.forEach(x -> x.toTLV().writeToBuffer(buffer));
        faultHandlerOverrides.forEach(x -> x.toTLV().writeToBuffer(buffer));
        flowLabel.writeToBuffer(buffer);
    }

    @Override
    protected CfdpHeader createHeader() {
        // TODO Auto-generated method stub
        return null;
    }

}
