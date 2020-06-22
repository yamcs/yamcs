package org.yamcs.simulation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.DataFile;
import org.yamcs.cfdp.DataFileSegment;
import org.yamcs.cfdp.FileDirective;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;

/**
 * Receives CFDP files.
 * <p>
 * It doesn't store them but just print a message at the end of the reception.
 * 
 * @author nm
 *
 */
public class CfdpReceiver {
    private static final Logger log = LoggerFactory.getLogger(CfdpReceiver.class);
    final Simulator simulator;

    private DataFile cfdpDataFile = null;
    List<SegmentRequest> missingSegments;

    public CfdpReceiver(Simulator simulator) {
        this.simulator = simulator;
    }

    protected void processCfdp(ByteBuffer buffer) {
        CfdpPacket packet = CfdpPacket.getCFDPPacket(buffer);
        if (packet.getHeader().isFileDirective()) {
            processFileDirective(packet);
        } else {
            processFileData((FileDataPacket) packet);
        }
    }

    private void processFileDirective(CfdpPacket packet) {
        switch (((FileDirective) packet).getFileDirectiveCode()) {
        case EOF:
            // 1 in 2 chance that we did not receive the EOF packet
            if (Math.random() > 0.5) {
                log.warn("EOF CFDP packet received and dropped (data loss simulation)");
                break;
            }
            processEofPacket((EofPacket) packet);
            break;
        case Finished:
            log.info("Finished CFDP packet received");
            break;
        case ACK:
            log.info("ACK CFDP packet received");
            break;
        case Metadata:
            log.info("Metadata CFDP packet received");
            MetadataPacket metadata = (MetadataPacket) packet;
            long packetLength = metadata.getFileLength();
            cfdpDataFile = new DataFile(packetLength);
            missingSegments = null;
            break;
        case NAK:
            log.info("NAK CFDP packet received");
            break;
        case Prompt:
            log.info("Prompt CFDP packet received");
            break;
        case KeepAlive:
            log.info("KeepAlive CFDP packet received");
            break;
        default:
            log.error("CFDP packet of unknown type received");
            break;
        }
    }

    private void processEofPacket(EofPacket packet) {
        ConditionCode code = packet.getConditionCode();
        if (code != ConditionCode.NoError) {
            log.info("EOF CFDP packet received with error code {}; the transfer is aborted", code);
            return;
        }
        log.info("EOF CFDP packet received, sending back ACK (EOF) packet");

        CfdpHeader header = new CfdpHeader(
                true,
                true,
                false,
                false,
                packet.getHeader().getEntityIdLength(),
                packet.getHeader().getSequenceNumberLength(),
                packet.getHeader().getSourceId(),
                packet.getHeader().getDestinationId(),
                packet.getHeader().getSequenceNumber());
        AckPacket EofAck = new AckPacket(
                FileDirectiveCode.EOF,
                FileDirectiveSubtypeCode.FinishedByWaypointOrOther,
                ConditionCode.NoError,
                TransactionStatus.Active,
                header);
        transmitCfdp(EofAck);

        log.info("ACK (EOF) sent, delaying a bit and sending Finished packet");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // checking the file completeness;
        missingSegments = cfdpDataFile.getMissingChunks();
        if (missingSegments.isEmpty()) {
            saveFile();

            log.info("Sending back finished PDU");
            header = new CfdpHeader(
                    true, // file directive
                    true, // towards sender
                    false, // not acknowledged
                    false, // no CRC
                    packet.getHeader().getEntityIdLength(),
                    packet.getHeader().getSequenceNumberLength(),
                    packet.getHeader().getSourceId(),
                    packet.getHeader().getDestinationId(),
                    packet.getHeader().getSequenceNumber());

            FinishedPacket finished = new FinishedPacket(ConditionCode.NoError,
                    true, // data complete
                    FileStatus.SuccessfulRetention,
                    null,
                    header);

            transmitCfdp(finished);
        } else {
            header = new CfdpHeader(
                    true, // file directive
                    true, // towards sender
                    false, // not acknowledged
                    false, // no CRC
                    packet.getHeader().getEntityIdLength(),
                    packet.getHeader().getSequenceNumberLength(),
                    packet.getHeader().getSourceId(),
                    packet.getHeader().getDestinationId(),
                    packet.getHeader().getSequenceNumber());

            NakPacket nak = new NakPacket(
                    missingSegments.get(0).getSegmentStart(),
                    missingSegments.get(missingSegments.size() - 1).getSegmentEnd(),
                    missingSegments,
                    header);

            log.info("File not complete ({} segments missing), sending NAK", missingSegments.size());
            transmitCfdp(nak);
        }
    }

    private void saveFile() {
        try {
            File f = File.createTempFile("cfdp", "");
            FileOutputStream fw = new FileOutputStream(f);
            fw.write(cfdpDataFile.getData());
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processFileData(FileDataPacket packet) {
        if (missingSegments == null || missingSegments.isEmpty()) {
            // we're not in "resending mode"
            // 1 in 5 chance to 'lose' the packet
            if (Math.random() > 0.8) {
                log.warn("Received and dropped (data loss simulation) {}", packet);
            } else {
                log.info("Received {}", packet);
                cfdpDataFile.addSegment(new DataFileSegment(packet.getOffset(), packet.getData()));
            }
        } else {
            // we're in resending mode, no more data loss
            cfdpDataFile.addSegment(new DataFileSegment(packet.getOffset(), packet.getData()));
            missingSegments = cfdpDataFile.getMissingChunks();
            log.info("Received missing data: {} still missing: {}", packet, missingSegments.size());
            if (missingSegments.isEmpty()) {
                saveFile();

                CfdpHeader header = new CfdpHeader(
                        true, // file directive
                        true, // towards sender
                        false, // not acknowledged
                        false, // no CRC
                        packet.getHeader().getEntityIdLength(),
                        packet.getHeader().getSequenceNumberLength(),
                        packet.getHeader().getSourceId(),
                        packet.getHeader().getDestinationId(),
                        packet.getHeader().getSequenceNumber());

                FinishedPacket finished = new FinishedPacket(
                        ConditionCode.NoError,
                        false, // data complete
                        FileStatus.SuccessfulRetention,
                        null,
                        header);

                transmitCfdp(finished);
            }
        }

    }

    protected void transmitCfdp(CfdpPacket packet) {
        CfdpHeader header = packet.getHeader();

        int length = 16 + header.getLength() + header.getDataLength();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.putShort((short) 0x17FD);
        buffer.putShort(4, (short) (length - 7));
        buffer.position(16);
        packet.writeToBuffer(buffer.slice());

        simulator.transmitRealtimeTM(new CCSDSPacket(buffer));
    }
}
