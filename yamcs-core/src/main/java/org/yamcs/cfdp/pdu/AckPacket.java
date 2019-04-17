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
        Undefined((byte) 0x00),
        Active((byte) 0x01),
        Terminated((byte) 0x02),
        Unrecognized((byte) 0x03);

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
        FinishedByWaypointOrOther((byte) 0x00),
        FinishedByEndSystem((byte) 0x01);

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
        finishConstruction();
    }

    public AckPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);
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
        buffer.put((byte) (this.directiveCode.getCode() << 4 | this.directiveSubtypeCode.getCode()));
        buffer.put((byte) (this.conditionCode.getCode() << 4 | this.transactionStatus.getStatus()));
    }

    @Override
    protected int calculateDataFieldLength() {
        return 3;
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.ACK;
    }

    public FileDirectiveSubtypeCode getFileDirectiveSubtypeCode() {
        return directiveSubtypeCode;
    }
}
