package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;

public class SpeedSpec {
    public enum Type {
        AFAP, FIXED_DELAY, ORIGINAL, STEP_BY_STEP
    }

    private Type type;
    private float multiplier = 1; // speed multiplier (for ORIGINAL)
    private int x = 1000; // DELAY in ms for FIXED_DELAY and STEP_BY_STEP
    String column; // for type=ORIGINAL

    public SpeedSpec(Type type) {
        this.type = type;
    }

    public SpeedSpec(Type type, int x) {
        this.type = type;
        this.x = x;
    }

    public SpeedSpec(Type type, String column, float multiplier) {
        this.type = type;
        this.column = column;
        this.multiplier = multiplier;
    }

    public long getFixedDelay() {
        return x;
    }

    public float getMultiplier() {
        return multiplier;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "SpeedSpec(type: " + type + " multiplier: " + multiplier + " delay: " + x + ")";
    }

    public ReplaySpeed toProtobuf() {
        ReplaySpeed.Builder rsb = ReplaySpeed.newBuilder();
        switch (type) {
        case AFAP:
            rsb.setType(ReplaySpeedType.AFAP);
            break;
        case FIXED_DELAY:
            rsb.setType(ReplaySpeedType.FIXED_DELAY);
            rsb.setParam(x);
            break;
        case ORIGINAL:
            rsb.setType(ReplaySpeedType.REALTIME);
            rsb.setParam(multiplier);
            break;
        case STEP_BY_STEP:
            rsb.setType(ReplaySpeedType.STEP_BY_STEP);
            break;
        }

        return rsb.build();
    }

    public static SpeedSpec fromProtobuf(ReplaySpeed speed) {
        SpeedSpec ss;
        switch (speed.getType()) {
        case AFAP:
        case STEP_BY_STEP: // Step advancing is controlled from within this class
            ss = new SpeedSpec(SpeedSpec.Type.AFAP);
            break;
        case FIXED_DELAY:
            ss = new SpeedSpec(SpeedSpec.Type.FIXED_DELAY, (int) speed.getParam());
            break;
        case REALTIME:
            ss = new SpeedSpec(SpeedSpec.Type.ORIGINAL, "gentime", speed.getParam());
            break;
        default:
            throw new IllegalArgumentException("Unknown speed type " + speed.getType());
        }
        return ss;
    }
}
