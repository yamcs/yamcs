package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.yamcs.cfdp.FileDirective;

import com.google.common.collect.Maps;

public class AckPacket extends CfdpPacket implements FileDirective {
    private FileDirectiveCode directiveCode;
    private FileDirectiveSubtypeCode directiveSubtypeCode;
    private ConditionCode conditionCode;
    private TransactionStatus transactionStatus;

    public enum TransactionStatus {
        UNDEFINED((byte) 0x00), ACTIVE((byte) 0x01), TERMINATED((byte) 0x02), URECOGNIZED((byte) 0x03);

        private byte status;

        public static final Map<Byte, TransactionStatus> Lookup = Maps.uniqueIndex(
                Arrays.asList(TransactionStatus.values()),
                TransactionStatus::getStatus);

        private TransactionStatus(byte status) {
            this.status = status;
        }

        public byte getStatus() {
            return status;
        }

        private static TransactionStatus fromStatus(byte status) {
            return Lookup.get(status);
        }

        public static TransactionStatus readTransactionStatus(byte b) {
            return TransactionStatus.fromStatus((byte) (b & 0x03));
        }

    }

    public enum FileDirectiveSubtypeCode {
        FINISHED_BY_WAYPOINT_OR_OTHER((byte) 0x00), FINISHED_BY_END_SYSTEM((byte) 0x01);

        private byte code;

        public static final Map<Byte, FileDirectiveSubtypeCode> Lookup = Maps.uniqueIndex(
                Arrays.asList(FileDirectiveSubtypeCode.values()),
                FileDirectiveSubtypeCode::getCode);

        private FileDirectiveSubtypeCode(byte code) {
            this.code = code;
        }

        public byte getCode() {
            return code;
        }

        private static FileDirectiveSubtypeCode fromCode(byte code) {
            return Lookup.get(code);
        }

        public static FileDirectiveSubtypeCode readSubtypeCode(byte b) {
            return FileDirectiveSubtypeCode.fromCode((byte) (b & 0x0f));
        }
    }

    public AckPacket(FileDirectiveCode code, FileDirectiveSubtypeCode subcode, ConditionCode conditionCode,
            TransactionStatus status, CfdpHeader header) {
        super(header);
        this.directiveCode = code;
        this.directiveSubtypeCode = subcode;
        this.conditionCode = conditionCode;
        this.transactionStatus = status;
    }

    AckPacket(ByteBuffer buffer, CfdpHeader header) {
        super(header);
        byte temp = buffer.get();
        this.directiveCode = FileDirectiveCode.readFileDirectiveCodeFromHalfAByte(temp);
        this.directiveSubtypeCode = FileDirectiveSubtypeCode.readSubtypeCode(temp);
        temp = buffer.get();
        this.conditionCode = ConditionCode.readConditionCode(temp);
        this.transactionStatus = TransactionStatus.readTransactionStatus(temp);
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        buffer.put((byte) (this.directiveCode.getCode() << 4 | this.directiveSubtypeCode.getCode() & 0xff));
        buffer.put((byte) (this.conditionCode.getCode() << 4 | this.transactionStatus.getStatus() & 0xff));
    }

    @Override
    public int getDataFieldLength() {
        return 3;
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.ACK;
    }

    public FileDirectiveCode getDirectiveCode() {
        return this.directiveCode;
    }

    public FileDirectiveSubtypeCode getFileDirectiveSubtypeCode() {
        return directiveSubtypeCode;
    }

    public ConditionCode getConditionCode() {
        return conditionCode;
    }

    @Override
    public String toString() {
        return "AckPacket [directiveCode=" + directiveCode + ", directiveSubtypeCode=" + directiveSubtypeCode
                + ", conditionCode=" + conditionCode + ", transactionStatus=" + transactionStatus + "]";
    }
}
