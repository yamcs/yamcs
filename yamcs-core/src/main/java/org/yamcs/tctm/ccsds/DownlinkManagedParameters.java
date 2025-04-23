package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.SdlsSecurityAssociation;
import org.yamcs.tctm.Link;
import org.yamcs.utils.YObjectLoader;

/**
 * Stores configuration related to Master channels for downlink.
 *
 * @author nm
 */
public abstract class DownlinkManagedParameters {
    public enum FrameErrorDetection {
        NONE, CRC16, CRC32
    };

    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;

    /**
     * A map of Security Parameter Indices to Security Associations
     */
    final Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public DownlinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName", null);
        errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class);

        if (config.containsKey("encryption")) {
            List<YConfiguration> encryptionConfigs = config.getConfigList("encryption");
            // Create all security associations according to the config
            for (YConfiguration saDef : encryptionConfigs) {
                short spi = (short) saDef.getInt("spi");
                byte[] sdlsKey;
                try {
                    sdlsKey = Files.readAllBytes(Path.of(saDef.getString("keyFile")));
                } catch (IOException e) {
                    throw new ConfigurationException(e);
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

    abstract int getMinFrameLength();

    abstract public Map<Integer, VcDownlinkHandler> createVcHandlers(String yamcsInstance, String linkName);

    protected VcDownlinkHandler createVcaHandler(String yamcsInstance, String linkName,
                                                 VcDownlinkManagedParameters vmp) {
        VcDownlinkHandler handler = YObjectLoader.loadObject(vmp.vcaHandlerClassName);
        if (handler instanceof Link) {
            ((Link) handler).init(yamcsInstance, linkName + ".vc" + vmp.vcId, vmp.config);
        }
        return handler;
    }
}