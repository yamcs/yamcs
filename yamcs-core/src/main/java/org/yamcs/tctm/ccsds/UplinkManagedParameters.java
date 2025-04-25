package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;
import org.yamcs.security.SdlsSecurityAssociation;

/**
 * Stores configuration related to Master channels for uplink.
 *
 * @author nm
 */
public abstract class UplinkManagedParameters {
    public enum FrameErrorDetection {NONE, CRC16, CRC32};

    public enum ServiceType {
        PACKET
    };


    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;

    /**
     * A map of Security Parameter Indices to Security Associations
     */
    final Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public UplinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName", null);
        this.errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class, FrameErrorDetection.CRC16);
        if (config.containsKey("encryption")) {
            List<YConfiguration> encryptionConfigs = config.getConfigList("encryption");
            for (YConfiguration saDef : encryptionConfigs) {
                short spi = (short) saDef.getInt("spi");
                byte[] sdlsKey;
                try {
                    sdlsKey = Files.readAllBytes(Path.of(saDef.getString("keyFile")));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                int encryptionSeqNumWindow = Math.abs(saDef.getInt("seqNumWindow"));
                boolean verifySeqNum = saDef.getBoolean("verifySeqNum");

                // Save the SPI and its security association
                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(sdlsKey, spi, encryptionSeqNumWindow,
                        verifySeqNum));
            }
        }
    }

    abstract int getMaxFrameLength();

    abstract public List<VcUplinkHandler> createVcHandlers(String yamcsInstance, String linkName,
                                                           ScheduledThreadPoolExecutor executor);
}