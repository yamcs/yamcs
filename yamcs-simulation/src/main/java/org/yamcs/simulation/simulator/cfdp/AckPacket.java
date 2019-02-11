package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public class AckPacket extends Packet {

    private Header header;
    private ByteBuffer buffer;

    private FileDirectiveCode directiveCode;
    private FileDirectiveSubtypeCode directiveSubtypeCode;
    private ConditionCode conditionCode;
    private TransactionStatus transactionStatus;

    public enum TransactionStatus {
        Undefined(0),
        Active(1),
        Terminated(2),
        Unrecognized(3);

        private int status;

        public static final Map<Integer, TransactionStatus> Lookup = Maps.uniqueIndex(
                Arrays.asList(TransactionStatus.values()),
                TransactionStatus::getStatus);

        private TransactionStatus(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

        private static TransactionStatus fromStatus(int status) {
            return Lookup.get(status);
        }

        public static TransactionStatus readTransactionStatus(byte b) {
            return TransactionStatus.fromStatus(b & 0x03);
        }

    }

    public enum FileDirectiveSubtypeCode {
        FinishedByWaypoint(0),
        FinishedByEndSystem(1),
        Other(0);

        private int code;

        public static final Map<Integer, FileDirectiveSubtypeCode> Lookup = Maps.uniqueIndex(
                Arrays.asList(FileDirectiveSubtypeCode.values()),
                FileDirectiveSubtypeCode::getCode);

        private FileDirectiveSubtypeCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        private static FileDirectiveSubtypeCode fromCode(int code) {
            return Lookup.get(code);
        }

        public static FileDirectiveSubtypeCode readSubtypeCode(byte b) {
            return FileDirectiveSubtypeCode.fromCode(b & 0x0f);
        }
    }

    public AckPacket(ByteBuffer buffer, Header header) {
        this.header = header;
        this.buffer = buffer;

        byte temp = buffer.get();
        this.directiveCode = FileDirectiveCode.readFileDirectiveCode(temp);
        this.directiveSubtypeCode = FileDirectiveSubtypeCode.readSubtypeCode(temp);
        temp = buffer.get();
        this.conditionCode = ConditionCode.readConditionCode(temp);
    }
}
