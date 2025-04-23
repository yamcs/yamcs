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

                // No need to authenticate data, already part of GCM
                // (source: McGrew and Viega, "The Galois/Counter Mode of Operation (GCM)").
                // Create an auth mask for the primary header, according to CCSDS Standard for
                // Space Data Link Security (CCSDS 355.0-B-2).
                // The SDLS implementation automatically adds the security header to authenticated data.
                // TODO: check all these masks are correct. account for maximum header size.
                if (this instanceof AosManagedParameters) {
                    // Auth mask with the size of the AOS primary header
                    authMask = new byte[10];
                    // Authenticate only virtual channel ID.
                    // We never authenticate the optional insert zone or Frame Header Error Control field.
                    authMask[1] = 0b0011_1111;
                } else if (this instanceof TmManagedParameters) {
                    // Auth mask with the size of the TM primary header
                    authMask = new byte[6];
                    // Authenticate only virtual channel ID.
                    // We never authenticate the Master Channel Frame Count field.
                    authMask[1] = 0b0000_1110;
                } else if (this instanceof UslpManagedParameters) {
                    // Auth mask with the size of the USLP primary header
                    authMask = new byte[14];
                    // Authenticate virtual channel ID and MAP ID
                    authMask[2] = 0b111; // top 3 bits of vcid
                    authMask[3] = (byte) 0b1111_1110; // bottom 3 bits of vcid, 4 bits of map id
                    // We never authenticate the optional insert zone.
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
