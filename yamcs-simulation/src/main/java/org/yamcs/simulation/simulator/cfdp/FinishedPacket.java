package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

public class FinishedPacket extends Packet {

    private Header header;
    private ByteBuffer buffer;
    private ConditionCode conditionCode;
    private boolean generatedByEndSystem;
    private boolean dataComplete;
    private FileStatus fileStatus;
    private TLV faultLocation;
    private List<FileStoreResponse> filestoreResponses = new ArrayList<FileStoreResponse>();;

    public enum FileStatus {
        DeliberatelyDiscarded(0),
        FilestoreRejection(1),
        SuccessfulRetention(2),
        FileStatusUnreported(3);

        private int code;

        public static final Map<Integer, FileStatus> Lookup = Maps.uniqueIndex(
                Arrays.asList(FileStatus.values()),
                FileStatus::getCode);

        private FileStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        private static FileStatus fromCode(int code) {
            return Lookup.get(code);
        }
    }

    public FinishedPacket(ByteBuffer buffer, Header header) {
        this.header = header;
        this.buffer = buffer;

        byte temp = buffer.get();
        this.conditionCode = ConditionCode.readConditionCode(temp);
        this.generatedByEndSystem = Utils.getBitOfByte(temp, 5);
        this.dataComplete = !Utils.getBitOfByte(temp, 6);
        this.fileStatus = FileStatus.fromCode(temp & 0x03);

        while (buffer.get() != buffer.limit()) {
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

}
