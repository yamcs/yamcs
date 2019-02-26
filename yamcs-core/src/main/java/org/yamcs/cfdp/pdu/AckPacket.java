package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public class AckPacket extends CfdpPacket {

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
        FinishedByWaypoint((byte) 0x00),
        FinishedByEndSystem((byte) 0x01),
        Other((byte) 0x00);

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

    public AckPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);
        byte temp = buffer.get();
        this.directiveCode = FileDirectiveCode.readFileDirectiveCode(temp);
        this.directiveSubtypeCode = FileDirectiveSubtypeCode.readSubtypeCode(temp);
        this.conditionCode = ConditionCode.readConditionCode(buffer.get());
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put((byte) (this.directiveCode.getCode() << 4 | this.directiveSubtypeCode.getCode()));
        buffer.put((byte) (this.conditionCode.getCode() << 4 | this.transactionStatus.getStatus()));
    }

    @Override
    protected CfdpHeader createHeader() {
        // TODO Auto-generated method stub
        return null;
    }
}
