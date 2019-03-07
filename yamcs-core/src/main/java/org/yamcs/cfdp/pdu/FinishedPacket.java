package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.yamcs.utils.CfdpUtils;

import com.google.common.collect.Maps;

public class FinishedPacket extends CfdpPacket {

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

    public FinishedPacket(ConditionCode code, boolean generatedByEndSystem, boolean deliveryCode, FileStatus status,
            List<FileStoreResponse> responses, TLV faultLocation, CfdpHeader header) {
        super(header);
        this.conditionCode = code;
        this.generatedByEndSystem = generatedByEndSystem;
        this.dataComplete = deliveryCode;
        this.fileStatus = status;
        this.filestoreResponses = responses;
        this.faultLocation = faultLocation;
        finishConstruction();
    }

    public FinishedPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        byte temp = buffer.get();
        this.conditionCode = ConditionCode.readConditionCode(temp);
        this.generatedByEndSystem = CfdpUtils.getBitOfByte(temp, 5);
        this.dataComplete = !CfdpUtils.getBitOfByte(temp, 6);
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
        buffer.put(FileDirectiveCode.Finished.getCode());
        byte temp = (byte) ((this.conditionCode.getCode() << 4));
        temp |= ((this.generatedByEndSystem ? 1 : 0) << 3);
        temp |= ((this.dataComplete ? 0 : 1) << 2);
        temp |= ((this.fileStatus.getCode() & 0x03));
        buffer.put(temp);
        this.filestoreResponses.forEach(x -> x.toTLV().writeToBuffer(buffer));
        if (faultLocation != null) {
            faultLocation.writeToBuffer(buffer);
        }
    }

    @Override
    protected int calculateDataFieldLength() {
        int toReturn = 1; // condition code + some status bits
        for (FileStoreResponse fsr : this.filestoreResponses) {
            toReturn += 2 // first byte of the FileStoreResponse + 1 time a LV length
                    + fsr.getFirstFileName().getValue().length;
            if (fsr.getSecondFileName() != null) {
                toReturn += 1 + fsr.getSecondFileName().getValue().length;
            }
            toReturn += 1 + fsr.getFilestoreMessage().getValue().length;
        }
        if (faultLocation != null) {
            toReturn += 2 + faultLocation.getValue().length;
        }
        return toReturn;
    }

}
