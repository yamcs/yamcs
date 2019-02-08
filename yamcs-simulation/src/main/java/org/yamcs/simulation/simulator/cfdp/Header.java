package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;

public class Header {

    /*
    Header (Variable size):
        3 bits      = Version ('000')
        1 bit       = PDU type (0 = File Directive, 1 = File Data)
        1 bit       = Direction (0 = towards receiver, 1 = towards sender)
        1 bit       = Transmission mode (0 = acknowledged, 1 = unacknowledged)
        1 bit       = CRC flag (0 = CRC not present, 1 = CRC present)
        1 bit       = reserved ('0')
        16 bits     = PDU data field length (in octets)
        1 bit       = reserved ('0')
        3 bits      = length of entity IDs (number of octets in entity ID minus 1)
        1 bit       = reserved ('0')
        3 bits      = length of transaction sequence number (number of octets in sequence number minus 1)
        variable    = source entity id (UINT)
        variable    = transaction sequence number (UINT)
        variable    = destination entity id (UINT)
    */

    // header types
    private boolean fileDirective, towardsSender, acknowledged, withCrc;
    private int dataLength, entityIdLength, sequenceNumberLength;
    private Long sourceId, destinationId, sequenceNr;
    private FileDirectiveParameter fileDirectiveParameter = null;

    private interface FileDirectiveParameter {}

    private enum FileDirectiveConditionCode {
        NoError(0),
        PositiveAckLimitReached(1),
        KeepAliveLimitReached(2),
        InvalidTransmissionMode(3),
        FilestoreRejection(4),
        FileChecksumFailure(5),
        FileSizeError(6),
        NakLimitReached(7),
        InactivityDetected(8),
        InvalidFileStructure(9),
        CheckLimitReached(10),
        SuspendRequestReceived(14),
        CancelRequestReceived(15),
        Reserved(11);

        private int code;

        public static final Map<Integer, FileDirectiveConditionCode> Lookup = Maps.uniqueIndex(
                Arrays.asList(FileDirectiveConditionCode.values()),
                FileDirectiveConditionCode::getCode);

        private FileDirectiveConditionCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static FileDirectiveConditionCode fromCode(int code) {
            if (Lookup.containsKey(code)) {
                return Lookup.get(code);
            } else {
                return Reserved;
            }
        }
    }

    public Header(ByteBuffer buffer) {
        readPduHeader(buffer);
    }

    public boolean isFileDirective() {
        return fileDirective;
    }

    /*
     * Reads the header of the incoming buffer, which is assumed to be a complete PDU
     * Afterwards puts the buffer position right after the header 
     */
    private void readPduHeader(ByteBuffer buffer) {
        buffer.position(0);
        byte tempByte = buffer.get();
        fileDirective = !Utils.getBitOfByte(tempByte, 4);
        towardsSender = Utils.getBitOfByte(tempByte, 5);
        acknowledged = !Utils.getBitOfByte(tempByte, 6);
        withCrc = Utils.getBitOfByte(tempByte, 7);
        dataLength = Utils.getUnsignedShort(buffer);
        tempByte = buffer.get();
        entityIdLength = (tempByte >> 4) & 0x07;
        sequenceNumberLength = tempByte & 0x07;
        sourceId = Utils.getUnsignedLongFromBuffer(buffer, entityIdLength);
        sequenceNr = Utils.getUnsignedLongFromBuffer(buffer, sequenceNumberLength);
        destinationId = Utils.getUnsignedLongFromBuffer(buffer, entityIdLength);
    }

}
