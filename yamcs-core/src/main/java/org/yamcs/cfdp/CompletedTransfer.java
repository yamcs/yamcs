package org.yamcs.cfdp;

import org.yamcs.YamcsServer;
import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Represents a past {@link CfdpFileTransfer} obtained from the database. Reads all the properties from a tuple (row in
 * the database).
 * <p>
 * Implements also some methods for converting between on-going transfers and tuples.
 * 
 * @author nm
 *
 */
public class CompletedTransfer implements CfdpFileTransfer {
    public static final TupleDefinition TDEF = new TupleDefinition();
    static final Log log = new Log(CompletedTransfer.class);
    static final String COL_ID = "id";
    static final String COL_SERVER_ID = "serverId";
    static final String COL_BUCKET = "bucket";
    static final String COL_START_TIME = "startTime";
    static final String COL_CREATION_TIME = "creationTime";
    static final String COL_OBJECT_NAME = "objectName";
    static final String COL_REMOTE_PATH = "remotePath";
    static final String COL_DIRECTION = "direction";
    static final String COL_SOURCE_ID = "sourceId";
    static final String COL_DESTINATION_ID = "destinationId";
    static final String COL_TOTAL_SIZE = "totalSize";
    static final String COL_TRANSFERED_SIZE = "transferredSize";
    static final String COL_RELIABLE = "reliable";
    static final String COL_SEQUENCE_NUMBER = "sequenceNumber";
    static final String COL_TRANSFER_STATE = "transferState";
    static final String COL_FAILURE_REASON = "failureReason";
    static final String COL_TRANSFER_TYPE = "transferType";

    static final String SERVER_ID = YamcsServer.getServer().getServerId();

    static {
        TDEF.addColumn(COL_ID, DataType.LONG);
        TDEF.addColumn(COL_SERVER_ID, DataType.ENUM);
        TDEF.addColumn(COL_START_TIME, DataType.TIMESTAMP);
        TDEF.addColumn(COL_BUCKET, DataType.STRING);
        TDEF.addColumn(COL_OBJECT_NAME, DataType.STRING);
        TDEF.addColumn(COL_REMOTE_PATH, DataType.STRING);
        TDEF.addColumn(COL_DIRECTION, DataType.ENUM);
        TDEF.addColumn(COL_SOURCE_ID, DataType.LONG);
        TDEF.addColumn(COL_DESTINATION_ID, DataType.LONG);
        TDEF.addColumn(COL_SEQUENCE_NUMBER, DataType.INT);
        TDEF.addColumn(COL_TOTAL_SIZE, DataType.LONG);
        TDEF.addColumn(COL_TRANSFERED_SIZE, DataType.LONG);
        TDEF.addColumn(COL_RELIABLE, DataType.BOOLEAN);
        TDEF.addColumn(COL_TRANSFER_STATE, DataType.STRING);
        TDEF.addColumn(COL_CREATION_TIME, DataType.TIMESTAMP);
        TDEF.addColumn(COL_TRANSFER_TYPE, DataType.STRING);
    }
    final Tuple tuple;

    public CompletedTransfer(Tuple tuple) {
        this.tuple = tuple;
    }

    @Override
    public long getStartTime() {
        if (tuple.hasColumn(COL_START_TIME)) {
            return tuple.getTimestampColumn(COL_START_TIME);
        } else {
            return TimeEncoding.INVALID_INSTANT;
        }
    }

    @Override
    public long getId() {
        return tuple.getLongColumn(COL_ID);
    }

    @Override
    public String getObjectName() {
        return tuple.getColumn(COL_OBJECT_NAME);
    }

    @Override
    public String getRemotePath() {
        return tuple.getColumn(COL_REMOTE_PATH);
    }

    @Override
    public TransferDirection getDirection() {
        String str = tuple.getColumn(COL_DIRECTION);
        if (str == null) {
            log.warn("No transfer direction retrieved from the archive");
            return null;
        }

        try {
            return TransferDirection.valueOf(str);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown transfer direction {} retrieved from the archive", str);
        }
        return null;
    }

    @Override
    public long getTotalSize() {
        Long l = tuple.getColumn(COL_TOTAL_SIZE);
        return l == null ? -1 : l;
    }

    @Override
    public long getTransferredSize() {
        Long l = tuple.getColumn(COL_TRANSFERED_SIZE);
        return l == null ? -1 : l;
    }

    @Override
    public String getBucketName() {
        return tuple.getColumn(COL_BUCKET);
    }

    @Override
    public CfdpTransactionId getTransactionId() {
        if (tuple.hasColumn(COL_SEQUENCE_NUMBER)) {
            return new CfdpTransactionId(tuple.getLongColumn(COL_SOURCE_ID),
                    tuple.getIntColumn(COL_SEQUENCE_NUMBER));
        } else {
            return null;
        }
    }

    @Override
    public TransferState getTransferState() {
        String str = tuple.getColumn(COL_TRANSFER_STATE);
        try {
            return TransferState.valueOf(str);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown transfer state {} retrieved from archive", str);
        }
        return null;
    }

    @Override
    public boolean isReliable() {
        return tuple.getColumn(COL_RELIABLE);
    }

    @Override
    public String getFailuredReason() {
        return tuple.getColumn(COL_FAILURE_REASON);
    }

    static Tuple toInitialTuple(CfdpFileTransfer transfer) {
        Tuple t = new Tuple();
        t.addColumn(COL_ID, transfer.getId());
        t.addColumn(COL_SERVER_ID, SERVER_ID);
        t.addTimestampColumn(COL_CREATION_TIME, transfer.getCreationTime());
        if (transfer.getBucketName() != null) {
            t.addColumn(COL_BUCKET, transfer.getBucketName());
        }

        if (transfer.getObjectName() != null) {
            t.addColumn(COL_OBJECT_NAME, transfer.getObjectName());
        }
        if (transfer.getRemotePath() != null) {
            t.addColumn(COL_REMOTE_PATH, transfer.getRemotePath());
        }
        t.addEnumColumn(COL_DIRECTION, transfer.getDirection().name());
        t.addColumn(COL_TOTAL_SIZE, transfer.getTotalSize());
        t.addColumn(COL_RELIABLE, transfer.isReliable());
        t.addColumn(COL_SOURCE_ID, transfer.getInitiatorEntityId());

        CfdpTransactionId txId = transfer.getTransactionId();
        if (txId != null) {// queued transfers have no transaction id
            t.addColumn(COL_SEQUENCE_NUMBER, txId.getSequenceNumber());
        }
        t.addColumn(COL_DESTINATION_ID, transfer.getDestinationId());
        t.addEnumColumn(COL_TRANSFER_STATE, transfer.getTransferState().name());
        t.addColumn(COL_TRANSFER_TYPE, transfer.getTransferType());
        return t;
    }

    static Tuple toUpdateTuple(FileTransfer transfer) {
        Tuple t = new Tuple();
        t.addColumn(COL_ID, transfer.getId());
        t.addColumn(COL_SERVER_ID, SERVER_ID);
        if (transfer.getBucketName() != null) {
            t.addColumn(COL_BUCKET, transfer.getBucketName());
        }
        t.addTimestampColumn(COL_START_TIME, transfer.getStartTime());
        t.addEnumColumn(COL_TRANSFER_STATE, transfer.getTransferState().name());
        t.addColumn(COL_TOTAL_SIZE, transfer.getTotalSize());
        t.addColumn(COL_TRANSFERED_SIZE, transfer.getTransferredSize());
        t.addColumn(COL_TRANSFER_TYPE, transfer.getTransferType());

        t.addColumn(COL_FAILURE_REASON, transfer.getFailuredReason());
        if (transfer.getDirection() == TransferDirection.DOWNLOAD) {
            // the object name is updated when saved in a bucket
            t.addColumn(COL_OBJECT_NAME, transfer.getObjectName());
        }

        return t;
    }

    @Override
    public boolean pausable() {
        return false;
    }

    @Override
    public boolean cancellable() {
        return false;
    }

    @Override
    public String toString() {
        return tuple.toString();
    }

    @Override
    public long getInitiatorEntityId() {
        return tuple.getLongColumn(COL_SOURCE_ID);
    }

    @Override
    public long getDestinationId() {
        return tuple.getLongColumn(COL_DESTINATION_ID);
    }

    @Override
    public long getCreationTime() {
        if (tuple.hasColumn(COL_CREATION_TIME)) {
            return tuple.getTimestampColumn(COL_CREATION_TIME);
        } else {
            return TimeEncoding.INVALID_INSTANT;
        }
    }

    @Override
    public String getTransferType() {
        return tuple.getColumn(COL_TRANSFER_TYPE);
    }
}
