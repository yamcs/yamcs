package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MetadataPacket extends Packet {

    private Header header;
    private ByteBuffer buffer;
    private boolean segmentationControl;
    private long fileSize;
    private LV sourceFileName;
    private LV destinationFileName;
    private List<FileStoreRequest> filestoreRequests = new ArrayList<FileStoreRequest>();
    private List<MessageToUser> messagesToUser = new ArrayList<MessageToUser>();
    private List<FaultHandlerOverride> faultHandlerOverrides = new ArrayList<FaultHandlerOverride>();
    private TLV flowLabel;

    public MetadataPacket(ByteBuffer buffer, Header header) {
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

}
