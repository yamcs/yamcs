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
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.StartS13DownlinkPacket;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.StartS13UplinkPacket;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.packets.UplinkS13Packet;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.requests.PutRequest;
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
    private long partSequenceNumber = 0;

    private boolean suspended = false;
    private PutRequest request;
    private ScheduledFuture<?> packetSendingSchedule;

    ConditionCode reasonForCancellation;

    public static boolean cop1Bypass;
    public static String firstPacketCmdName;
    public static String intermediatePacketCmdName;
    public static String lastPacketCmdName;
    public static int maxDataSize;

    public S13OutgoingTransfer(String yamcsInstance, long initiatorEntityId, long transferId, long largePacketTransactionId, long creationTime,
            ScheduledThreadPoolExecutor executor,
            PutRequest request, YConfiguration config, Bucket bucket,
            Integer customPacketSize, Integer customPacketDelay,
            EventProducer eventProducer, TransferMonitor monitor, String transferType,
            Map<ConditionCode, FaultHandlingAction> faultHandlerActions) {

        super(yamcsInstance, transferId, creationTime, executor, config, makeTransactionId(initiatorEntityId, transferId,
                largePacketTransactionId), request.getDestinationId(), eventProducer, monitor, transferType, faultHandlerActions);
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
    }

    private static S13TransactionId makeTransactionId(long sourceId, long transferId, long largePacketTransactionId) {
        return new S13TransactionId(sourceId, transferId, largePacketTransactionId);
    }

    /**
     * Start the transfer
     */
    public void start() {
        packetSendingSchedule = executor.scheduleAtFixedRate(this::sendS13Packet, 0, sleepBetweenPackets, TimeUnit.MILLISECONDS);
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

                if (request.getFileDownloadRequestPacket() != null) {
                    packet = request.getFileDownloadRequestPacket();
                    try {
                        sendPacket(packet);
                    } catch (CommandEncodingException e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
                        return;
                    }
                    complete(ConditionCode.NO_ERROR);

                } else {    // First Packet
                    fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(firstPacketCmdName, request.getDestinationId());
                    packet = new StartS13UplinkPacket(s13TransactionId, partSequenceNumber, fullyQualifiedCmdName, getFilePart());
                    sentPackets.add(packet);
                    try{
                        sendPacket(packet);
                    } catch (CommandEncodingException e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
                        return;
                    }

                    transferred += (end - offset);
                    offset = end;

                    this.outTxState = OutTxState.SENDING_DATA;
                }
                monitor.stateChanged(this);
                break;

            case SENDING_DATA:
                if (request.getFileLength() - offset < maxDataSize) {   // Last Packet
                    end = Math.min(offset + maxDataSize, request.getFileLength());
                    partSequenceNumber++;

                    fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(lastPacketCmdName, request.getDestinationId());
                    packet = new StartS13UplinkPacket(s13TransactionId, partSequenceNumber, fullyQualifiedCmdName, getFilePart());
                    sentPackets.add(packet);
                    try {
                        sendPacket(packet);
                    } catch (CommandEncodingException e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
                        return;
                    }

                    transferred += (end - offset);
                    offset = end;
                    
                    complete(ConditionCode.NO_ERROR);

                } else {    // Intermediate Packet
                    end = Math.min(offset + maxDataSize, request.getFileLength());
                    partSequenceNumber++;

                    fullyQualifiedCmdName = ServiceThirteen.constructFullyQualifiedCmdName(intermediatePacketCmdName, request.getDestinationId());
                    packet = new StartS13UplinkPacket(s13TransactionId, partSequenceNumber, fullyQualifiedCmdName, getFilePart());
                    sentPackets.add(packet);
                    try {
                        sendPacket(packet);
                    } catch (CommandEncodingException e) {
                        pushError(e.toString());
                        handleFault(ConditionCode.UNSUPPORTED_CHECKSUM_TYPE);
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

        String eventMessageSuffix;
        if (request.getFileLength() > 0) {
            eventMessageSuffix = request.getSourceFileName() + " -> " + request.getDestinationFileName();
        } else {
            String remoteEntityName = ServiceThirteen.getEntityFromId(s13TransactionId.getLargePacketTransactionId(), ServiceThirteen.remoteEntities).getName();
            eventMessageSuffix = "Fileless transfer: \n" 
                                    + "     Large Packet Transaction ID: " + s13TransactionId.getLargePacketTransactionId() + "\n"
                                    + "     Remote Source entity name: " + remoteEntityName + "\n";
        }
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
                    break;
                case CANCEL:
                    cancel(conditionCode);
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
                reasonForCancellation = conditionCode;
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
    public long getInitiatorEntityId() {
        return getInitiatorId();
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
