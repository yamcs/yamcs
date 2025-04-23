package org.yamcs.tctm.ccsds;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.SdlsSecurityAssociation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Stores configuration related to Master channels for uplink.
 * 
 * @author nm
 *
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
    Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public UplinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName",  null);
        this.errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class, FrameErrorDetection.CRC16);
        if (config.containsKey("encryption")) {
            List<YConfiguration> encryptionConfigs = config.getConfigList("encryption");
            for (YConfiguration saDef : encryptionConfigs) {
                byte[] authMask;
                short spi = (short) saDef.getInt("spi");
                byte[] sdlsKey;
                try {
                    sdlsKey = Files.readAllBytes(Path.of(saDef.getString("keyFile")));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // No need to authenticate data, already part of GCM
                // (source: McGrew and Viega, "The Galois/Counter Mode of Operation (GCM)").
                // Create an auth mask for the primary header, according to CCSDS Standard for
                // Space Data Link Security (CCSDS 355.0-B-2).
                // The SDLS implementation automatically adds the security header to authenticated data.
                // TODO: check if this is correct. account for max header size.
                if (this instanceof TcManagedParameters) {
                    // Create an auth mask with the size of the TC primary header
                    authMask = new byte[5];
                    // Authenticate virtual channel ID
                    // TODO: double check segment header
                    authMask[2] = (byte) 0b1111_1100; // authenticate virtual channel ID
                } else {
                    throw new ConfigurationException("Encryption not supported for " + this);
                }

                // Save the SPI and its security association
                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(sdlsKey, spi, authMask));
            }
        }
    }
    
    abstract int getMaxFrameLength();
    
    abstract public  List<VcUplinkHandler> createVcHandlers(String yamcsInstance, String linkName, ScheduledThreadPoolExecutor executor);
}
