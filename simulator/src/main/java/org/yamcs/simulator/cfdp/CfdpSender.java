package org.yamcs.simulator.cfdp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.ChecksumCalculator;
import org.yamcs.cfdp.ChecksumType;
import org.yamcs.cfdp.pdu.*;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.simulator.AbstractSimulator;

public class CfdpSender {
    final static int PDU_SIZE = 1000;
    final static int ENTITY_ID_LENGTH = 1;
    final static int SEQ_NR_LENGTH = 4;
    final static int EOF_ACK_LIMIT = 5;

    private static final Logger log = LoggerFactory.getLogger(CfdpReceiver.class);
    static AtomicInteger sequenceNumberGenerator = new AtomicInteger();

    final AbstractSimulator simulator;

    File file;
    static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    boolean hasToSendMetadata;
    boolean dataFinished = false;
    ArrayDeque<DataToResend> resendQueue = new ArrayDeque<>();

    private ScheduledFuture<?> dataSenderFuture, eofSenderFuture;
    private RandomAccessFile raf;
    private int fileSize;
    CfdpHeader directiveHeader;
    final CfdpTransactionId cfdpTransactionId;
    long myEntityId = 5;
    long dataOffset = 0;
    long checksum = 0;

    private CfdpHeader dataHeader;
    int eofAckCount = 0;

    private String destinationFileName;
    private List<TLV> metadataOptions;
    // allow to simulate packet loss by skipping some pdus
    int[] skippedPdus;
    int skipIdx = 0;
    int pduCount = 0;

    private List<Runnable> endCallbacks = new ArrayList<>();

    public CfdpSender(AbstractSimulator simulator, int destinationId, File file, String destinationFileName,
            List<TLV> metadataOptions, int[] skippedPdus)
            throws FileNotFoundException {
        this.simulator = simulator;
        this.file = file;
        this.destinationFileName = destinationFileName;
        this.metadataOptions = metadataOptions;
        this.skippedPdus = skippedPdus;
        this.raf = new RandomAccessFile(file, "r");
        if (file.length() > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("Large files not supported");
        }
        this.fileSize = (int) file.length();
        this.cfdpTransactionId = new CfdpTransactionId(myEntityId, sequenceNumberGenerator.getAndIncrement());
        directiveHeader = new CfdpHeader(
                true, // it's a file directive
                false, // it's sent towards the receiver
                true, // acknowledged
                false, // no CRC
                ENTITY_ID_LENGTH, // entityIdLength
                SEQ_NR_LENGTH, // seq nr length
                cfdpTransactionId.getInitiatorEntity(), // my Entity Id
                destinationId, // the id of the target
                cfdpTransactionId.getSequenceNumber());

        dataHeader = new CfdpHeader(
                false, // it's file data
                false, // it's sent towards the receiver
                true, // acknowledged
                false, // no CRC
                ENTITY_ID_LENGTH,
                SEQ_NR_LENGTH,
                cfdpTransactionId.getInitiatorEntity(), // my Entity Id
                destinationId, // the id of the target
                cfdpTransactionId.getSequenceNumber());

    }

    public void start() {
        hasToSendMetadata = true;
        dataSenderFuture = executor.scheduleAtFixedRate(() -> {
            if (hasToSendMetadata) {
                sendMetadata();
                hasToSendMetadata = false;
            } else if (!dataFinished) {
                sendData();
            } else if (!resendQueue.isEmpty()) {
                resendData();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

    }

    public void processCfdp(ByteBuffer buffer) {
        CfdpPacket packet = CfdpPacket.getCFDPPacket(buffer);
        executor.submit(() -> processIncomingPacket(packet));
    }

    private void processIncomingPacket(CfdpPacket packet) {
        if (packet instanceof NakPacket) {
            NakPacket nak = (NakPacket) packet;
            resendQueue.clear();
            for (SegmentRequest sr : nak.getSegmentRequests()) {
                if (sr.getSegmentStart() == 0 && sr.getSegmentEnd() == 0) {
                    hasToSendMetadata = true;
                } else {
                    for (long offset = sr.getSegmentStart(); offset < sr.getSegmentEnd(); offset += PDU_SIZE) {
                        long end = Math.min(offset + PDU_SIZE, sr.getSegmentEnd());
                        resendQueue.add(new DataToResend(offset, end));
                    }
                }
            }
        } else if (packet instanceof AckPacket) {
            if (eofSenderFuture == null) {
                log.error("EOF ACK received but EOF not sent");
            } else {
                log.info("CFDP received EOF ACK");
                eofSenderFuture.cancel(true);
            }
        } else if (packet instanceof FinishedPacket) {
            processFinishedPacket((FinishedPacket) packet);
        }
    }

    private void sendData() {
        long end = Math.min(dataOffset + PDU_SIZE, fileSize);
        sendFileData(dataOffset, end, true);
        dataOffset = end;
        if (dataOffset == fileSize) {
            eofSenderFuture = executor.scheduleAtFixedRate(() -> sendEof(), 0, 2000, TimeUnit.MILLISECONDS);
            dataFinished = true;
        }
    }

    private void resendData() {
        DataToResend dtr = resendQueue.poll();
        if (dtr == null) {
            return;
        }
        sendFileData(dtr.start, dtr.end, false);
    }

    private void sendEof() {
        eofAckCount++;
        if (eofAckCount >= EOF_ACK_LIMIT) {
            log.warn("EOF_ACK_LIMIT reached");
            eofSenderFuture.cancel(false);
        } else {
            log.info("CFDP sending EOF");
            EofPacket eof = new EofPacket(ConditionCode.NO_ERROR, checksum, fileSize, null, directiveHeader);
            transmitCfdp(eof);
        }
    }

    private void processFinishedPacket(FinishedPacket packet) {
        log.info("CFDP data sending finished; code:{}, data complete: {}", packet.getConditionCode(),
                packet.isDataComplete());
        dataSenderFuture.cancel(true);
        if (eofSenderFuture != null) {
            eofSenderFuture.cancel(true);
        }
        AckPacket ack = new AckPacket(FileDirectiveCode.FINISHED, FileDirectiveSubtypeCode.FINISHED_BY_END_SYSTEM,
                packet.getConditionCode(), TransactionStatus.TERMINATED, directiveHeader);
        transmitCfdp(ack);
        notifyEndCallbacks();
    }

    private void sendFileData(long start, long end, boolean addToChecksum) {
        log.info("CFDP sending data [{}, {}]", start, end);
        byte[] data = new byte[(int) (end - start)];
        try {
            raf.seek(start);
            raf.readFully(data);
        } catch (IOException e) {
            log.warn("Error reading from file", e);
            abort();
        }
        if (addToChecksum) {
            checksum += ChecksumCalculator.calculateChecksum(data);
            checksum &= 0xFFFFFFFF;
        }

        FileDataPacket fdp = new FileDataPacket(data, start, dataHeader);
        transmitCfdp(fdp);
    }

    private void abort() {
        dataSenderFuture.cancel(true);
        if (eofSenderFuture != null) {
            eofSenderFuture.cancel(true);
        }
    }

    private void sendMetadata() {
        MetadataPacket metadata = new MetadataPacket(false, ChecksumType.MODULAR, fileSize,
                file.getPath(), destinationFileName, metadataOptions, directiveHeader);
        transmitCfdp(metadata);
    }

    private void transmitCfdp(CfdpPacket packet) {
        boolean skip = false;
        while (skipIdx < skippedPdus.length && skippedPdus[skipIdx] < pduCount) {
            skipIdx++;
        }

        if (skipIdx < skippedPdus.length) {
            if (skippedPdus[skipIdx] == pduCount) {
                log.info("Dropping (simulating packet loss) PDU {}: {}", pduCount, packet);
                skip = true;
            }
        }
        pduCount++;
        if (!skip) {
            simulator.transmitCfdp(packet);
        }
    }

    static class DataToResend {
        final long start;
        final long end;

        DataToResend(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public void addEndCallback(Runnable runnable) {
        endCallbacks.add(runnable);
    }

    public void removeEndCallback(Runnable runnable) {
        endCallbacks.remove(runnable);
    }

    private void notifyEndCallbacks() {
        for (Runnable runnable : endCallbacks) {
            runnable.run();
        }
    }
}
