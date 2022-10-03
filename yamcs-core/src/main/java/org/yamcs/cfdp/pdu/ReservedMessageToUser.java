package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;

public class ReservedMessageToUser extends MessageToUser {

    public final static String MESSAGE_IDENTIFIER = "cfdp";

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
        SFO_FILESTORE_RESPONSE((byte) 0x46);

        private final byte[] bytes;

        MessageType(byte bytes) {
            this.bytes = new byte[] {bytes};
        }

        public byte[] getBytes() {
            return bytes;
        }
    }

    public ReservedMessageToUser(MessageType messageType, byte[] value) {
        super(Bytes.concat(MESSAGE_IDENTIFIER.getBytes(), messageType.getBytes(), value));
    }

}
