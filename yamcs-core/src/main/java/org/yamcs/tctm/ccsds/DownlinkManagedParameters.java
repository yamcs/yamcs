package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.memento.MementoDb;
import org.yamcs.security.SdlsMemento;
import org.yamcs.security.SdlsSecurityAssociation;
import org.yamcs.tctm.Link;
import org.yamcs.utils.YObjectLoader;

/**
 * Stores configuration related to Master channels for downlink.
 *
 */
public abstract class DownlinkManagedParameters {
    public enum FrameErrorDetection {
        NONE, CRC16, CRC32
    };

    protected String physicalChannelName;
    protected int spacecraftId;
    protected FrameErrorDetection errorDetection;
    protected String linkName;

    /**
     * A map of Security Parameter Indices to Security Associations
     */
    final Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public DownlinkManagedParameters(YConfiguration config, String yamcsInstance, String linkName) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName", null);
        errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class);
        this.linkName = linkName;

        if (config.containsKey("encryption")) {
            List<YConfiguration> encryptionConfigs = config.getConfigList("encryption");
            // Create all security associations according to the config
            Set<Short> newSpis = new HashSet<>(encryptionConfigs.size());
            for (YConfiguration saDef : encryptionConfigs) {
                short spi = (short) saDef.getInt("spi");
                newSpis.add(spi);
                byte[] sdlsKey;
                try {
                    sdlsKey = Files.readAllBytes(Path.of(saDef.getString("keyFile")));
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                }

                boolean verifySeqNum = saDef.getBoolean("verifySeqNum");
                int encryptionSeqNumWindow = verifySeqNum ? Math.abs(saDef.getInt("seqNumWindow")) : 1;
                byte[] initialSeqNum = saDef.getBinary("initialSeqNum", null);

                var sa = new SdlsSecurityAssociation(yamcsInstance, linkName, sdlsKey, spi,
                        initialSeqNum, encryptionSeqNumWindow, verifySeqNum);
                // Save the SPI and its security association
                sdlsSecurityAssociations.put(spi, sa);
            }

            // Clear out seq numbers for any removed SPIs
            var mementoDb = MementoDb.getInstance(yamcsInstance);
            var maybeMemento = mementoDb.getObject(SdlsMemento.MEMENTO_KEY, SdlsMemento.class);
            if (maybeMemento.isPresent()) {
                var memento = maybeMemento.get();
                var oldSpis = memento.getSpis(linkName);
                oldSpis.removeAll(newSpis);
                for (var spi : oldSpis) {
                    memento.delSeqNum(linkName, spi);
                }
                mementoDb.putObject(SdlsMemento.MEMENTO_KEY, memento);
            }
        }
    }

    abstract int getMaxFrameLength();

    abstract int getMinFrameLength();

    abstract public Map<Integer, VcDownlinkHandler> createVcHandlers(String yamcsInstance, String linkName);

    abstract public VcDownlinkManagedParameters getVcParams(int vcId);

    protected VcDownlinkHandler createVcaHandler(String yamcsInstance, String linkName,
                                                 VcDownlinkManagedParameters vmp) {
        VcDownlinkHandler handler = YObjectLoader.loadObject(vmp.vcaHandlerClassName);
        if (handler instanceof Link) {
            ((Link) handler).init(yamcsInstance, linkName + ".vc" + vmp.vcId, vmp.config);
        }
        return handler;
    }
}