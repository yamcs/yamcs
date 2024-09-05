package org.yamcs.tctm.ccsds;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Master channels for uplink.
 * 
 * @author nm
 *
 */
public abstract class UplinkManagedParameters {
    public enum FrameErrorDetection {
        NONE("NONE"), CRC16("CRC16"), CRC32("CRC32");

        private final String value;

        FrameErrorDetection(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static FrameErrorDetection fromValue(String value) {
            for (FrameErrorDetection enumValue : FrameErrorDetection.values()) {
                if (enumValue.value == value) {
                    return enumValue;
                }
            }
            throw new IllegalArgumentException("No enum constant with value " + value);
        }
    };

    public enum ServiceType {
        PACKET
    };
    
    
    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;
    
    public UplinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName",  null);
        this.errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class, FrameErrorDetection.CRC16);
    }
    
    abstract int getMaxFrameLength();
    
    abstract public  List<VcUplinkHandler> createVcHandlers(String yamcsInstance, String linkName, ScheduledThreadPoolExecutor executor);
}
