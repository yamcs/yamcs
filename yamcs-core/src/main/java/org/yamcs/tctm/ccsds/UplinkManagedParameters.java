package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;
import org.yamcs.memento.MementoDb;
import org.yamcs.security.SdlsMemento;
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
    protected String linkName;

    /**
     * A map of Security Parameter Indices to Security Associations
     */
    final Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public UplinkManagedParameters(YConfiguration config, String yamcsInstance, String linkName) {
        this.spacecraftId = config.getInt("spacecraftId");
        this.physicalChannelName = config.getString("physicalChannelName", null);
        this.errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class, FrameErrorDetection.CRC16);
        this.linkName = linkName;
        if (config.containsKey("encryption")) {
            List<YConfiguration> encryptionConfigs = config.getConfigList("encryption");
            Set<Short> newSpis = new HashSet<>(encryptionConfigs.size());
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
                newSpis.add(spi);
                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(yamcsInstance, linkName, sdlsKey, spi,
                        encryptionSeqNumWindow,
                        verifySeqNum));
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

    abstract public List<VcUplinkHandler> createVcHandlers(String yamcsInstance, String linkName,
                                                           ScheduledThreadPoolExecutor executor);
}