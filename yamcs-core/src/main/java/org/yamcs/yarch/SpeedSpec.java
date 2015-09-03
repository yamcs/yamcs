package org.yamcs.yarch;

public class SpeedSpec {
    public enum Type {AFAP, FIXED_DELAY, ORIGINAL, STEP_BY_STEP};
    private Type type;
    private float multiplier=1 ; //speed multiplier (for ORIGINAL) 
    private int x=1000; //DELAY in ms for FIXED_DELAY and STEP_BY_STEP
    String column; //for type=ORIGINAL


    public SpeedSpec(Type type) {
        this.type=type;
    }

    public SpeedSpec(Type type, int x ) {
        this.type=type;
        this.x=x;
    }

    public SpeedSpec(Type type, String column, float multiplier) {
        this.type=type;
        this.column=column;
        this.multiplier=multiplier;
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
        return "SpeedSpec(type: "+type+" multiplier: "+multiplier+" delay: "+x+")";
    }
}
