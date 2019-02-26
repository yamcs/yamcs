package org.yamcs.cfdp.pdu;

public class FaultHandlerOverride {

    public static byte TYPE = 0x04;

    private ConditionCode conditionCode;
    private HandlerCode handlerCode;

    public FaultHandlerOverride(ConditionCode conditionCode, HandlerCode handlerCode) {
        this.conditionCode = conditionCode;
        this.handlerCode = handlerCode;
    }

    public ConditionCode getConditionCode() {
        return this.conditionCode;
    }

    public HandlerCode getHandlerCode() {
        return this.handlerCode;
    }

    public static FaultHandlerOverride fromTLV(TLV tlv) {
        byte b = tlv.getValue()[0];
        return new FaultHandlerOverride(ConditionCode.readConditionCode(b), HandlerCode.readHandlerCode(b));
    }

    public TLV toTLV() {
        return new TLV(FaultHandlerOverride.TYPE,
                new byte[] { (byte) (getConditionCode().getCode() << 4
                        | getHandlerCode().getCode()) });
    }
}
