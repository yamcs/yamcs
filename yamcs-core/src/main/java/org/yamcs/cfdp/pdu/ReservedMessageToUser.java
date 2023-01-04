package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.utils.StringConverter;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ReservedMessageToUser extends MessageToUser {

    public static final String MESSAGE_IDENTIFIER = "cfdp";
    private final MessageType messageType;
    private final byte[] content;

    public enum MessageType {
        //00 Proxy Put Request
        //01 Proxy Message to User
        //02 Proxy Filestore Request
        //03 Proxy Fault Handler Override
        //04 Proxy Transmission Mode
        //05 Proxy Flow Label
        //06 Proxy Segmentation Control
        //07 Proxy Put Response
        //08 Proxy Filestore Response
        //09 Proxy Put Cancel
        //0B Proxy Closure Request
        //10 Directory Listing Request
        //11 Directory Listing Response
        //20 Remote Status Report Request
        //21 Remote Status Report Response
        //30 Remote Suspend Request
        //31 Remote Suspend Response
        //38 Remote Resume Request
        //39 Remote Resume Response
        //40 SFO Request
        //41 SFO Message to User
        //42 SFO Flow Label
        //43 SFO Fault Handler Override
        //44 SFO Filestore Request
        //45 SFO Report
        //46 SFO Filestore Response
        PROXY_PUT_REQUEST((byte) 0x00),
        PROXY_MESSAGE_TO_USER((byte) 0x01),
        PROXY_FILESTORE_REQUEST((byte) 0x02),
        PROXY_FAULT_HANDLER_OVERRIDE((byte) 0x03),
        PROXY_TRANSMISSION_MODE((byte) 0x04),
        PROXY_FLOW_LABEL((byte) 0x05),
        PROXY_SEGMENTATION_CONTROL((byte) 0x06),
        PROXY_PUT_RESPONSE((byte) 0x07),
        PROXY_FILESTORE_RESPONSE((byte) 0x08),
        PROXY_PUT_CANCEL((byte) 0x09),
        ORIGINATING_TRANSACTION_ID((byte) 0x0A),
        PROXY_CLOSURE_REQUEST((byte) 0x0B),
        DIRECTORY_LISTING_REQUEST((byte) 0x10),
        DIRECTORY_LISTING_RESPONSE((byte) 0x11),
        REMOTE_STATUS_REPORT_REQUEST((byte) 0x20),
        REMOTE_STATUS_REPORT_RESPONSE((byte) 0x21),
        REMOTE_SUSPEND_REQUEST((byte) 0x30),
        REMOTE_SUSPEND_RESPONSE((byte) 0x31),
        REMOTE_RESUME_REQUEST((byte) 0x38),
        REMOTE_RESUME_RESPONSE((byte) 0x39),
        SFO_REQUEST((byte) 0x40),
        SFO_MESSAGE_TO_USER((byte) 0x41),
        SFO_FLOW_LABEL((byte) 0x42),
        SFO_FAULT_HANDLER_OVERRIDE((byte) 0x43),
        SFO_FILESTORE_REQUEST((byte) 0x44),
        SFO_REPORT((byte) 0x45),
        SFO_FILESTORE_RESPONSE((byte) 0x46),
        UNKNOWN_MESSAGE_TYPE((byte) 0xFF); // 0xFF is arbitrary (not in CFDP spec)

        private final byte[] bytes;

        MessageType(byte bytes) {
            this.bytes = new byte[] {bytes};
        }

        public byte[] getBytes() {
            return bytes;
        }

        public static MessageType fromByte(byte b) {
            switch (b) {
                case 0x00: return PROXY_PUT_REQUEST;
                case 0x01: return PROXY_MESSAGE_TO_USER;
                case 0x02: return PROXY_FILESTORE_REQUEST;
                case 0x03: return PROXY_FAULT_HANDLER_OVERRIDE;
                case 0x04: return PROXY_TRANSMISSION_MODE;
                case 0x05: return PROXY_FLOW_LABEL;
                case 0x06: return PROXY_SEGMENTATION_CONTROL;
                case 0x07: return PROXY_PUT_RESPONSE;
                case 0x08: return PROXY_FILESTORE_RESPONSE;
                case 0x09: return PROXY_PUT_CANCEL;
                case 0x0A: return ORIGINATING_TRANSACTION_ID;
                case 0x0B: return PROXY_CLOSURE_REQUEST;
                case 0x10: return DIRECTORY_LISTING_REQUEST;
                case 0x11: return DIRECTORY_LISTING_RESPONSE;
                case 0x20: return REMOTE_STATUS_REPORT_REQUEST;
                case 0x21: return REMOTE_STATUS_REPORT_RESPONSE;
                case 0x30: return REMOTE_SUSPEND_REQUEST;
                case 0x31: return REMOTE_SUSPEND_RESPONSE;
                case 0x38: return REMOTE_RESUME_REQUEST;
                case 0x39: return REMOTE_RESUME_RESPONSE;
                case 0x40: return SFO_REQUEST;
                case 0x41: return SFO_MESSAGE_TO_USER;
                case 0x42: return SFO_FLOW_LABEL;
                case 0x43: return SFO_FAULT_HANDLER_OVERRIDE;
                case 0x44: return SFO_FILESTORE_REQUEST;
                case 0x45: return SFO_REPORT;
                case 0x46: return SFO_FILESTORE_RESPONSE;
                default: return UNKNOWN_MESSAGE_TYPE;
            }
        }

    }

    public ReservedMessageToUser(MessageType messageType, byte[] content) {
        super(Bytes.concat(MESSAGE_IDENTIFIER.getBytes(), messageType.getBytes(), content));

        this.messageType = messageType;
        this.content = content;
    }

    /**
     * Decodes ReservedMessageToUser from MessageToUser TLV value. Returns regular MessageToUser if unable.
     * Will return the child associated with the MessageType instead if the class exists.
     * @param value TLV value to decode
     * @return ReservedMessageToUser (or child if possible), or regular MessageToUser if unable
     */
    public static MessageToUser fromValue(byte[] value) {
        if(value.length < MESSAGE_IDENTIFIER.getBytes().length + 1 || !Arrays.equals(Arrays.copyOfRange(value, 0, MESSAGE_IDENTIFIER.getBytes().length), MESSAGE_IDENTIFIER.getBytes())) {
            // Not a CFDP reserved message to user
            return new MessageToUser(value);
        }

        ByteBuffer buffer = ByteBuffer.wrap(value);
        MessageType messageType = MessageType.fromByte(buffer.get(MESSAGE_IDENTIFIER.getBytes().length));
        buffer.position(MESSAGE_IDENTIFIER.getBytes().length + 1);
        byte[] content = new byte[buffer.remaining()];
        buffer.get(content);


        switch (messageType) {
        case PROXY_PUT_REQUEST:
            return new ProxyPutRequest(content);
        case PROXY_TRANSMISSION_MODE:
            return new ProxyTransmissionMode(content);
        case PROXY_PUT_RESPONSE:
            return new ProxyPutResponse(content);
        case ORIGINATING_TRANSACTION_ID:
            return new OriginatingTransactionId(content);
        case PROXY_CLOSURE_REQUEST:
            return new ProxyClosureRequest(content);
        case DIRECTORY_LISTING_REQUEST:
            return new DirectoryListingRequest(content);
        case DIRECTORY_LISTING_RESPONSE:
            return new DirectoryListingResponse(content);
        default:
            return new ReservedMessageToUser(messageType, content);
        }
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + messageType + ", content="
                + StringConverter.arrayToHexString(content) + "}";
    }

}
