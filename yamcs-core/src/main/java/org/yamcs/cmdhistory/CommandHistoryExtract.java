package org.yamcs.cmdhistory;

/**
 * Command history delivered "offline" to clients
 * @author nm
 *
 */
public class CommandHistoryExtract {
    public int subscriptionId;
    public String extract;
    public int sequenceCount;
    public boolean isLastDelivery;
    public String toString() {
        return "subscriptionId="+subscriptionId+" sequenceCount="+sequenceCount+" isLastDelivery: "+isLastDelivery+" extract: "+extract;
    }
}