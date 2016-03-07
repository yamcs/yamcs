package org.yamcs.algorithms;

import org.yamcs.parameter.ParameterValue;

/**
 * A history window is for looking up past values. Intentionally it
 * does not allow multiple values at the same generation-time
 */
public class WindowBuffer {
    // Sorted least-recent to most-recent
    private long[] genTimes;
    private ParameterValue[] historicValues;
    
    WindowBuffer(int size) {
        genTimes=new long[size];
        historicValues=new ParameterValue[size];
    }
    
    /** -1 goes back one */
    public ParameterValue getHistoricValue(int instance) {
        return historicValues[historicValues.length-1+instance];
    }
    
    void update(ParameterValue pval) {
        long genTime = pval.getGenerationTime();
        // Find index to insert at
        int index=-1;
        for(int i=genTimes.length-1; i>=0; i--) {
            if(genTimes[i]==genTime) {
                break; // Only one value per genTime
            } else if(genTimes[i]<genTime) {
                index=i;
                break;
            }
        }
        if(index==-1) {
            return; // Outdated value
        }
        
        // Shift older values to the left
        for(int i=0; i<index; i++) {
            genTimes[i]=genTimes[i+1];
            historicValues[i]=historicValues[i+1];
        }
        genTimes[index]=genTime;
        historicValues[index]=pval;
    }
    
    void expandIfNecessary(int newSize) {
        if(newSize>genTimes.length) {
            ParameterValue[] newPvals=new ParameterValue[newSize];
            long[] newGenTimes=new long[newSize];
            int diff=newSize-genTimes.length;
            for(int i=diff; i<newSize; i++) {
                newGenTimes[i]=genTimes[i-diff];
                newPvals[i]=historicValues[i-diff];
            }
            genTimes=newGenTimes;
            historicValues=newPvals;
        }
    }
    
    public int getSize() {
        return genTimes.length;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("[");
        buf.append(genTimes[0]).append("->").append(toValue(historicValues[0]));
        for (int i=1;i<genTimes.length;i++) {
            buf.append(", ").append(genTimes[i]).append("->").append(toValue(historicValues[i]));
        }
        return buf.append("]").toString();
    }
    
    private static String toValue(ParameterValue pval) {
        if(pval==null) return null;
        return pval.getEngValue().toString();
    }
}
