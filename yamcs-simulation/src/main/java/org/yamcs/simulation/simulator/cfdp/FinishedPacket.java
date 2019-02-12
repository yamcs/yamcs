package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

public class FinishedPacket extends Packet {

    private ConditionCode conditionCode;
    private boolean generatedByEndSystem;
    private boolean dataComplete;
    private FileStatus fileStatus;
    private TLV faultLocation = null;
    private List<FileStoreResponse> filestoreResponses = new ArrayList<FileStoreResponse>();

    public enum FileStatus {
        DeliberatelyDiscarded((byte) 0x00),
        FilestoreRejection((byte) 0x01),
        SuccessfulRetention((byte) 0x02),
        FileStatusUnreported((byte) 0x03);

        private byte code;

        public static final Map<Byte, FileStatus> Lookup = Maps.uniqueIndex(
                Arrays.asList(FileStatus.values()),
                FileStatus::getCode);

        private FileStatus(byte code) {
            this.code = code;
        }

        public byte getCode() {
            return code;
        }

        private static FileStatus fromCode(byte code) {
            return Lookup.get(code);
        }
    }

    public FinishedPacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        byte temp = buffer.get();
        this.conditionCode = ConditionCode.readConditionCode(temp);
        this.generatedByEndSystem = Utils.getBitOfByte(temp, 5);
        this.dataComplete = !Utils.getBitOfByte(temp, 6);
        this.fileStatus = FileStatus.fromCode((byte) (temp & 0x03));

        while (buffer.hasRemaining()) {
            TLV tempTLV = TLV.readTLV(buffer);
            switch (tempTLV.getType()) {
            case 1:
                this.filestoreResponses.add(FileStoreResponse.fromTLV(tempTLV));
                break;
            case 6:
                this.faultLocation = tempTLV;
                break;
            default: // TODO
            }
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        super.writeCFDPPacket(buffer);
        byte temp = (byte) ((this.conditionCode.getCode() << 4));
        temp |= ((this.generatedByEndSystem ? 1 : 0) << 3);
        temp |= ((this.dataComplete ? 0 : 1) << 2);
        temp |= ((this.fileStatus.getCode() & 0x03));
        buffer.put(temp);
        for (FileStoreResponse filestoreResponse : filestoreResponses) {
            filestoreResponse.toTLV().writeToBuffer(buffer);
        }
        if (faultLocation != null) {
            faultLocation.writeToBuffer(buffer);
        }
    }

}
