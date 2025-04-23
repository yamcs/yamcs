package org.yamcs.tctm.ccsds;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.SdlsSecurityAssociation;
import org.yamcs.tctm.Link;
import org.yamcs.utils.YObjectLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores configuration related to Master channels for downlink.
 * 
 * @author nm
 *
 */
public abstract class DownlinkManagedParameters {
    public enum FrameErrorDetection {
        NONE, CRC16, CRC32
    };

    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;
    Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public DownlinkManagedParameters(YConfiguration config) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName", null);
        errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class);
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

                if (this instanceof AosManagedParameters) {
                    // Create an auth mask for the primary header,
                    // the frame data is already part of authentication.
                    // No need to authenticate data, already part of GCM.
                    // We never authenticate the optional insert zone
                    // TODO: cite
                    authMask = new byte[10];
                    authMask[1] = 0b0011_1111; // authenticate only virtual channel ID
                } else if (this instanceof TmManagedParameters) {
                    // Create an auth mask for the primary header,
                    // the frame data is already part of authentication.
                    // No need to authenticate data, already part of GCM
                    authMask = new byte[6];
                    authMask[1] = 0b0000_1110; // authenticate virtual channel ID
                } else if (this instanceof UslpManagedParameters) {
                    // Authenticate virtual channel ID and MAP ID
                    authMask = new byte[14];
                    authMask[2] = 0b111; // top 3 bits of vcid
                    authMask[3] = (byte) 0b1111_1110; // bottom 3 bits of vcid, 4 bits of map id
                } else {
                    throw new ConfigurationException("Encryption not yet supported for " + this);
                }

                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(sdlsKey, spi, authMask));
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
