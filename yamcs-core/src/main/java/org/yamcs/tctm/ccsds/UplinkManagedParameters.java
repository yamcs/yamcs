package org.yamcs.tctm.ccsds;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.YConfiguration;
import org.yamcs.memento.MementoDb;
import org.yamcs.security.SdlsMemento;
import org.yamcs.security.sdls.SdlsSecurityAssociation;

/**
 * Stores configuration related to Master channels for uplink.
 */
public abstract class UplinkManagedParameters {
    public enum FrameErrorDetection {
        NONE, CRC16, CRC32
    }

    public enum ServiceType {
        PACKET
    }

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

                byte[] initialSeqNum = saDef.getBinary("initialSeqNum", null);

                // Save the SPI and its security association
                newSpis.add(spi);

                // Grab the auth mask from the config and convert it to byte[]
                byte[] customAuthMask = null;
                if (saDef.containsKey("authMask")) {
                    List<Integer> configAuthMask = saDef.getList("authMask");
                    customAuthMask = configAuthMask.stream()
                            .collect(
                                    java.io.ByteArrayOutputStream::new,
                                    ByteArrayOutputStream::write,
                                    (a, b) -> { try { b.writeTo(a); } catch (Exception e) { throw new RuntimeException(e);} }
                            )
                            .toByteArray();
                }
                sdlsSecurityAssociations.put(spi,
                        new SdlsSecurityAssociation(yamcsInstance, linkName, sdlsKey, spi,
                                customAuthMask,
                                initialSeqNum));
            }

            // Clear out seq numbers for any removed SPIs
            MementoDb mementoDb = MementoDb.getInstance(yamcsInstance);
            Optional<SdlsMemento> maybeMemento = mementoDb.getObject(SdlsMemento.MEMENTO_KEY, SdlsMemento.class);
            if (maybeMemento.isPresent()) {
                SdlsMemento memento = maybeMemento.get();
                Set<Short> oldSpis = memento.getSpis(linkName);
                oldSpis.removeAll(newSpis);
                for (Short spi : oldSpis) {
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