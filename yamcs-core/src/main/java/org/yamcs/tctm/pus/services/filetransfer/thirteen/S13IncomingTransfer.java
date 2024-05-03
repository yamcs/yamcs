package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import static org.yamcs.tctm.pus.services.filetransfer.thirteen.ServiceThirteen.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;
import org.yamcs.cfdp.Timer;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.FileSaveHandler;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.DownlinkS13Packet;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.FileTransferPacket;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.DownlinkS13Packet.PacketType;

public class S13IncomingTransfer extends OngoingS13Transfer {
    private final FileSaveHandler fileSaveHandler;

    private enum InTxState {
        /**
         * Receives metadata, data and EOF.
         * <p>
         * Sends NAK with missing segments.
         * <p>
         * Goes into FIN as soon as the EOF and all data has been received.
         * <p>
         * Also goes into FIN when an error is encountered or if the transaction is
         * cancelled.
         */
        RECEIVING_DATA,
        /**
         *
         */
        COMPLETED
    }

    private InTxState inTxState = InTxState.RECEIVING_DATA;
    private final String objectNamePrefix;
    private boolean suspended = false;
    private long transferred;
    private final long remoteId;
    private final String contentType;

    Timer checkTimer;

    // in case we start a transaction before receiving the metadata, this list will save the packets which will be
    // processed after we have the metadata
    List<DownlinkS13Packet> queuedPackets = new ArrayList<>();
    List<Long> partSequenceNumbers = new ArrayList<>();

    public S13IncomingTransfer(String yamcsInstance, long remoteId, long creationTime, ScheduledThreadPoolExecutor executor,
            YConfiguration config, S13TransactionId transactionId, String objectNamePrefix, FileSaveHandler fileSaveHandler,
            EventProducer eventProducer, TransferMonitor monitor, String transferType, String contentType,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {
        super(yamcsInstance, null, creationTime, executor, config, transactionId, eventProducer, monitor, transferType, faultHandlerActions);

        long checkAckTimeout = config.getLong("checkAckTimeout", 10000l);
        int checkAckLimit = config.getInt("checkAckLimit", 5);
        checkTimer = new Timer(executor, checkAckLimit, checkAckTimeout);

        this.fileSaveHandler = fileSaveHandler;
        this.objectNamePrefix = objectNamePrefix;
        String objectName = objectNamePrefix + "-" + startTime;

        this.contentType = contentType;
        this.remoteId = remoteId;

        try {
            fileSaveHandler.setObjectName(objectName);
        }
        catch (IOException e) {
            // FIXME: Can I do anything else to handle this?
            handleFault(ConditionCode.FILESTORE_REJECTION);
            log.warn(e.getMessage());
            pushError(e.getMessage());
        }

        rescheduleInactivityTimer();
    }

    private void handleFault(ConditionCode conditionCode) {
        switch (inTxState) {
            case RECEIVING_DATA:
                FaultHandlingAction action = getFaultHandlingAction(conditionCode);
                switch (action) {
                    case ABANDON:
                        complete(conditionCode);
                        break;
                    case CANCEL:
                        cancel(conditionCode);
                        break;
                    case SUSPEND:
                        suspend();
                        break;
                }
                break;
            case COMPLETED:
                break;
        }
    }

    protected void onInactivityTimerExpiration() {
        log.warn("TXID{} inactivity timer expired, state: {}", s13TransactionId, inTxState);

        switch (inTxState) {
            case RECEIVING_DATA:
                handleFault(ConditionCode.INACTIVITY_DETECTED);
                break;
            case COMPLETED:
                log.error("TXID{} Illegal state", s13TransactionId);
                break;
        }
    }

    private void complete(ConditionCode conditionCode) {
        inTxState = InTxState.COMPLETED;

        checkTimer.cancel();
        cancelInactivityTimer();

        if (conditionCode == ConditionCode.NO_ERROR) {
            changeState(TransferState.COMPLETED);
            sendInfoEvent(ETYPE_TRANSFER_FINISHED, " transfer completed successfully");
        } else {
            if(errors.isEmpty()) {
                pushError(conditionCode.toString());
            }
            sendWarnEvent(ETYPE_TRANSFER_FINISHED, " transfer completed unsuccessfully: " + getFailuredReason());
            changeState(TransferState.FAILED);
        }
    }

    private void checkFileComplete() {
        Collections.sort(partSequenceNumbers);
        int filePartCount = partSequenceNumbers.size();

        long expectedSum = (long) filePartCount * (filePartCount + 1) / 2; // Sum of numbers from 0 to n
        long actualSum = partSequenceNumbers.stream().mapToLong(Long::longValue).sum();

        if ((expectedSum - actualSum) == 0) {
            onFileCompleted();
            return;
        }

        // Incomplete file
        log.warn("TXID{} file checksum failure; EOF packet indicates numOfPacket {} while data received has {} packets",
                s13TransactionId, partSequenceNumbers.get(filePartCount - 1) + 1, filePartCount);

        List<Integer> missingSegments = getMissingSegments(partSequenceNumbers, partSequenceNumbers.get(filePartCount - 1) + 1);
        sendWarnEvent(ETYPE_TRANSFER_FINISHED,
                " checksum failure; corrupted file saved in " + getBucketName() + "/" + getObjectName());

        try {
            saveFile(missingSegments);
        } catch (IOException e) {
            sendWarnEvent(ETYPE_TRANSFER_FINISHED,
                    " downlink finished partially. However the file could not be saved in the intended location: "
                            + getBucketName() + "/" + getObjectName());
            pushError(ETYPE_TRANSFER_FINISHED + 
                    " downlink finished partially. However the file could not be saved in the intended location: "
                            + getBucketName() + "/" + getObjectName());
        }
        handleFault(ConditionCode.FILE_CHECKSUM_FAILURE);
    }

    private List<Integer> getMissingSegments(List<Long> numbersList, long n) {
        List<Integer> missingNumbers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!numbersList.contains((long) i)) {
                missingNumbers.add(i);
            }
        }
        return missingNumbers;
    }

    private void saveFile(List<Integer> missingSegments) throws IOException{
        Map<String, String> metadata = null;
        if (!missingSegments.isEmpty()) {
            metadata = new HashMap<>();

            StringJoiner joiner = new StringJoiner("");
            for (Integer number : missingSegments)
                joiner.add(number.toString());
            
            metadata.put("missingSegments", joiner.toString());
        }
        // FIXME: Edge case - Repeated duplicate packets are currently not handled
        queuedPackets.sort(Comparator.comparingLong(DownlinkS13Packet::getPartSequenceNumber));

        ByteArrayOutputStream fileDataStream = new ByteArrayOutputStream();
        for(DownlinkS13Packet packet: queuedPackets) {
            fileDataStream.write(packet.getFilePart());
        }

        fileSaveHandler.saveFile(fileDataStream.toByteArray(), metadata, getTransactionId(), contentType);
    }

    private void onFileCompleted() {
        log.info("TXID{} file completed, checksum OK", s13TransactionId);

        try {
            saveFile(Collections.emptyList());
            sendInfoEvent(ETYPE_TRANSFER_FINISHED,
                        " downlink finished and saved in " + getBucketName() + "/" + getObjectName());
            complete(ConditionCode.NO_ERROR);

        } catch(IOException e) {
            sendWarnEvent(ETYPE_TRANSFER_FINISHED, 
                        " downlink finished. However the file could not be saved in the intended location: " + getBucketName() + "/" + getObjectName());
            pushError(ETYPE_TRANSFER_FINISHED +
                    " downlink finished. However the file could not be saved in the intended location: "
                            + getBucketName() + "/" + getObjectName());
            complete(ConditionCode.FILESTORE_REJECTION);
        }

    }

    @Override
    public void processPacket(FileTransferPacket packet) {
        executor.execute(() -> doProcessPacket((DownlinkS13Packet) packet));
    }

    private void doProcessPacket(DownlinkS13Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("TXID{} received S13 Packet: {}", s13TransactionId, packet);
        }
        if(inTxState == InTxState.RECEIVING_DATA) {
            rescheduleInactivityTimer();
        }

        queuedPackets.add(packet);
        partSequenceNumbers.add(packet.getPartSequenceNumber());

        // Increment the size of the data transferred
        transferred += packet.getFilePart().length;

        if(packet.getPacketType() == PacketType.LAST) {
            checkTimer.start(this::checkFileComplete, () -> {
                log.warn("TXID{} check limit reached", s13TransactionId);
                handleFault(ConditionCode.CHECK_LIMIT_REACHED);
            });
            cancelInactivityTimer();
        }
    }

    @Override
    protected void suspend() {
        if (inTxState == InTxState.COMPLETED) {
            log.info("TXID{} transfer finished, suspend ignored", s13TransactionId);
            return;
        }
        log.info("TXID{} suspending transfer", s13TransactionId);

        sendInfoEvent(ETYPE_TRANSFER_SUSPENDED, "transfer suspended");
        changeState(TransferState.PAUSED);
        suspended = true;
    }

    @Override
    protected void cancel(ConditionCode code) {
        if (inTxState == InTxState.RECEIVING_DATA) {
            complete(code);
        } else {
            log.debug("TXID{} ignoring cancel, wrong state", s13TransactionId);
        }
    }

    @Override
    protected void resume() {
        if (!suspended) {
            log.info("TXID{} resume called while not suspended, ignoring", s13TransactionId);
            return;
        }
        if (inTxState == InTxState.COMPLETED) {
            // it is possible the transfer has finished while being suspended
            log.info("TXID{} transfer finished, resume ignored", s13TransactionId);
            return;
        }
        log.info("TXID{} resuming transfer", s13TransactionId);

        sendInfoEvent(ETYPE_TRANSFER_RESUMED, "transfer resumed");
        changeState(TransferState.RUNNING);

        suspended = false;
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.DOWNLOAD;
    }

    @Override
    public long getTotalSize() {
        // Impossible to know total size of Large file in S13
        return -1;
    }

    @Override
    public String getBucketName() {
        return fileSaveHandler.getBucketName();
    }

    @Override
    public String getObjectName() {
        return fileSaveHandler.getObjectName();
    }
    @Override
    public long getRemoteId() {
        return remoteId;
    }

    @Override
    public String getRemotePath() {
        return objectNamePrefix;
    }

    @Override
    public long getTransferredSize() {
        return this.transferred;
    }

    @Override
    public String getOrigin(){
        return this.origin;
    }

    @Override
    public String getTransferType() {
        return transferType;
    }
}
