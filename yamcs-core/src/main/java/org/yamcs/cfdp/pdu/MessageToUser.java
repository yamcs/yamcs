package org.yamcs.cfdp.pdu;

public class MessageToUser extends TLV {
    public MessageToUser(byte[] value) {
        super(TLV.TYPE_MESSAGE_TO_USER, value);
    }

    /**
     * Decodes TLV into a MessageToUser. Returns original TLV if unable
     * @param tlv TLV to decode
     * @return Corresponding MessageToUser, or original TLV if unable
     */
    public static TLV fromTLV(TLV tlv) {
        if(tlv.getType() != TLV.TYPE_MESSAGE_TO_USER) {
            // Not a message to user
            return tlv;
        }

        return ReservedMessageToUser.fromValue(tlv.getValue());
    }
}
