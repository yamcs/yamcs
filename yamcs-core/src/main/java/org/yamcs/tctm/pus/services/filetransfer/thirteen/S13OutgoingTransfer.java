package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import static org.yamcs.tctm.pus.services.filetransfer.thirteen.ServiceThirteen.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.YConfiguration;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.events.EventProducer;
import org.yamcs.filetransfer.TransferMonitor;
import org.yamcs.mdb.CommandEncodingException;
import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.FileTransferPacket;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.UplinkS13Packet;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.FilePutRequest;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.Stream;

public class S13OutgoingTransfer extends OngoingS13Transfer{
    
    private enum OutTxState {
        /**
         * Initial state.
         * Going to SENDING_DATA in the first sendPdu step.
         */
        START,
        /**
         * Sending data and EOF.
         * Going to FINISHED as soon as the Finished PDU is received.
         */
        SENDING_DATA,
        /**
         * Sending CANCEL EOF
         * Going to COMPLETED as soon as the CANCEL EOF ACK is received
         */
        CANCELING,
        /**
         * End state.
         * Still sending FINISHED ACK in return of Finished PDUs.
         */
        COMPLETED,
    }

    private Bucket bucket;
    private final int sleepBetweenPackets;

    private final List<FileTransferPacket> sentPackets = new ArrayList<>();

    private OutTxState outTxState;
    private long transferred;
    private String origin;

    private long offset = 0;
    private long end = 0;
    private long partSequenceNumber = 1;        // Start from 1, not 0

    private boolean suspended = false;
    private FilePutRequest request;
    private ScheduledFuture<?> packetSendingSchedule;

    public static boolean cop1Bypass;
    public static int maxDataSize;

    public String firstPacketCmdName;
    public String intermediatePacketCmdName;
    public String lastPacketCmdName;
    public boolean skipAcknowledgement;

    public S13OutgoingTransfer(String yamcsInstance, long transferInstanceId, long largePacketTransactionId, long creationTime,
            ScheduledThreadPoolExecutor executor, Stream cmdHistRealtime,
            FilePutRequest request, YConfiguration config, Bucket bucket,
            Integer customPacketSize, Integer customPacketDelay,
            EventProducer eventProducer, TransferMonitor monitor, String transferType,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {

        super(yamcsInstance, cmdHistRealtime, creationTime, executor, config, makeTransactionId(request.getRemoteId(), transferInstanceId, largePacketTransactionId), 
            eventProducer, monitor, transferType, faultHandlerActions);
        this.request = request;
        this.bucket = bucket;
        this.origin = ServiceThirteen.origin;
        int maxPacketSize = customPacketSize != null && customPacketSize > 0 ? customPacketSize : config.getInt("maxPacketSize", 512);
        maxDataSize = maxPacketSize - (PusTcManager.secondaryHeaderLength + PusTcManager.DEFAULT_PRIMARY_HEADER_LENGTH + 
                org.yamcs.tctm.pus.services.tc.thirteen.ServiceThirteen.largePacketTransactionIdSize + org.yamcs.tctm.pus.services.tc.thirteen.ServiceThirteen.partSequenceNumberSize);

        outTxState = OutTxState.START;
        this.sleepBetweenPackets = customPacketDelay != null && customPacketDelay > 0 ? customPacketDelay
                : config.getInt("sleepBetweenPackets", 500);
        
        cop1Bypass = config.getBoolean("useCop1Bypass", true);
        firstPacketCmdName = config.getString("firstPacketCmdName", "FirstUplinkPart");
        intermediatePacketCmdName = config.getString("intermediatePacketCmdName", "IntermediateUplinkPart");
        lastPacketCmdName = config.getString("lastPacketCmdName", "LastUplinkPart");
        skipAcknowledgement = config.getBoolean("skipAcknowledgement", true);
    }

    private static S13TransactionId makeTransactionId(long remoteId, long transferInstanceId, long largePacketTransactionId) {
        return new S13TransactionId(remoteId, transferInstanceId, largePacketTransactionId, TransferDirection.UPLOAD);
    }

    /**
     * Start the transfer
     */
    public void start() {
        packetSendingSchedule = executor.scheduleWithFixedDelay(this::sendS13Packet, 0, sleepBetweenPackets, TimeUnit.MILLISECONDS);
    }

    public byte[] getFilePart() {
        return Arrays.copyOfRange(request.getFileData(), (int) offset, (int) end);
    }

    private void sendS13Packet() {
        if (suspended) {
            return;
        }

        UplinkS13Packet packet;
        String fullyQualifiedCmdName;
        switch (outTxState) {
            case START:
                offset = 0; // first file data packet starts at the start of the data
                end = Math.min(maxDataSize, request.getFileLength());

                // First Packet
                fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(firstPacketCmdName, request.getRemoteId());
                packet = new UplinkS13Packet(s13TransactionId, partSequenceNumber, fullyQualifiedCmdName, getFilePart(), skipAcknowledgement);
                sentPackets.add(packet);
                try{
                    sendPacket(packet);

                } catch (CommandEncodingException e) {
                    pushError(e.toString());
                    handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
                    return;

                } catch (Exception e) {
                    pushError(e.toString());
                    handleFault(ConditionCode.NAK_LIMIT_REACHED);
                    return;
                }


                transferred += (end - offset);
                offset = end;

                this.outTxState = OutTxState.SENDING_DATA;

                monitor.stateChanged(this);
                break;

            case SENDING_DATA:
                if (request.getFileLength() - offset < maxDataSize) {   // Last Packet
                    end = Math.min(offset + maxDataSize, request.getFileLength());
                    partSequenceNumber++;

                    fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(lastPacketCmdName, request.getRemoteId());
                    packet = new UplinkS13Packet(s13TransactionId, partSequenceNumber, fullyQualifiedCmdName, getFilePart(), skipAcknowledgement);
                    sentPackets.add(packet);
                    try {
                        sendPacket(packet);

                    } catch (CommandEncodingException e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
                        return;

                    } catch (Exception e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.NAK_LIMIT_REACHED);
                        return;
                    }


                    transferred += (end - offset);
                    offset = end;
                    
                    complete(ConditionCode.NO_ERROR);

                } else {    // Intermediate Packet
                    end = Math.min(offset + maxDataSize, request.getFileLength());
                    partSequenceNumber++;

                    fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(intermediatePacketCmdName, request.getRemoteId());
                    packet = new UplinkS13Packet(s13TransactionId, partSequenceNumber, fullyQualifiedCmdName, getFilePart(), skipAcknowledgement);
                    sentPackets.add(packet);
                    try {
                        sendPacket(packet);

                    } catch (CommandEncodingException e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
                        return;

                    } catch (Exception e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.NAK_LIMIT_REACHED);
                        return;
                    }
                    transferred += (end - offset);
                    offset = end;
                }
                monitor.stateChanged(this);
                break;

            case COMPLETED:
                packetSendingSchedule.cancel(true);
                break;

            default:
                throw new IllegalStateException("unknown/illegal state");
        }
    }

    private void complete(ConditionCode conditionCode) {
        if (outTxState == OutTxState.COMPLETED) {
            return;
        }
        outTxState = OutTxState.COMPLETED;
        long duration = (System.currentTimeMillis() - wallclockStartTime) / 1000;

        String eventMessageSuffix = request.getSourceFileName() + " -> " + request.getDestinationFileName();

        if (conditionCode == ConditionCode.NO_ERROR) {
            changeState(TransferState.COMPLETED);
            sendInfoEvent(ETYPE_TRANSFER_FINISHED,
                    "transfer finished successfully in " + duration + " seconds: "
                            + eventMessageSuffix);
        } else {
            failTransfer(conditionCode.toString());
            sendWarnEvent(ETYPE_TRANSFER_FINISHED,
                    "transfer finished with error in " + duration + " seconds: "
                            + eventMessageSuffix + ", error: " + conditionCode);
        }

    }

    private void handleFault(ConditionCode conditionCode) {
        log.debug("TXID{} Handling fault {}", s13TransactionId, conditionCode);

        if (outTxState == OutTxState.CANCELING) {
            complete(conditionCode);
        } else {
            FaultHandlingAction action = getFaultHandlingAction(conditionCode);
            switch (action) {
                case ABANDON:
                    complete(conditionCode);
                    pushError(conditionCode.toString());
                    break;
                case CANCEL:
                    cancel(conditionCode);
                    pushError(conditionCode.toString());
                    break;
                case SUSPEND:
                    suspend();
            }
        }
    }

    @Override
    protected void cancel(ConditionCode conditionCode) {
        log.debug("TXID{} Cancelling with code {}", s13TransactionId, conditionCode);
        switch (outTxState) {
            case START:
            case SENDING_DATA:
                suspended = false; // wake up if sleeping
                outTxState = OutTxState.CANCELING;
                changeState(TransferState.CANCELLING);
                handleFault(conditionCode);
                break;
            case CANCELING:
            case COMPLETED:
                break;
        }
    }

    @Override
    protected void suspend() {
        if (outTxState == OutTxState.COMPLETED) {
            log.info("TXID{} transfer finished, suspend ignored", s13TransactionId);
            return;
        }

        sendInfoEvent(ETYPE_TRANSFER_SUSPENDED, "transfer suspended");
        log.info("TXID{} suspending transfer", s13TransactionId);

        packetSendingSchedule.cancel(true);
        suspended = true;

        changeState(TransferState.PAUSED);
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.UPLOAD;
    }
    
    @Override
    public long getTotalSize() {
        return this.request.getFileLength();
    }

    @Override
    public String getBucketName() {
        return bucket != null ? bucket.getName() : null;
    }

    @Override
    public String getObjectName() {
        return request.getSourceFileName();
    }

    @Override
    public String getRemotePath() {
        return request.getDestinationFileName();
    }

    @Override
    public long getRemoteId() {
        return request.getRemoteId();
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
    protected void resume() {
        if (!suspended) {
            log.info("TXID{} resume called while not suspended, ignoring", s13TransactionId);
            return;
        }
        if (outTxState == OutTxState.COMPLETED) {
            // it is possible the transfer has finished while being suspended
            log.info("TXID{} transfer finished, suspend ignored", s13TransactionId);
            return;
        }

        log.info("TXID{} resuming transfer", s13TransactionId);
        sendInfoEvent(ETYPE_TRANSFER_RESUMED, "transfer resumed");

        packetSendingSchedule = executor.scheduleAtFixedRate(this::sendS13Packet, 0, sleepBetweenPackets, TimeUnit.MILLISECONDS);

        changeState(TransferState.RUNNING);
        suspended = false;
    }

    @Override
    public String getTransferType() {
        return transferType;
    }

    @Override
    public void processPacket(FileTransferPacket packet) {
        throw new UnsupportedOperationException("Unimplemented method 'processPacket'");
    }

    @Override
    protected void onInactivityTimerExpiration() {
        throw new UnsupportedOperationException("Unimplemented method 'onInactivityTimerExpiration'");
    }
}
