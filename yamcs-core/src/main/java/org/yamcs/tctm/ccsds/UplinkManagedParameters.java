package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.memento.MementoDb;
import org.yamcs.security.sdls.SdlsMemento;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.security.sdls.SdlsSecurityAssociationFactory;

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
     * An object to hold the Security Association and auth mask associated with a Security Paramter Index
     * @param sa the security association
     * @param customAuthMask the auth mask to use. If null, a default is used based on the CCSDS standards.
     */
    record SdlsInfo(SdlsSecurityAssociation sa, byte[] customAuthMask) {
        public SdlsInfo {
            Objects.requireNonNull(sa);
        }
    }

    /**
     * A map of Security Parameter Indices to Security Associations
     */
    final Map<Short, SdlsInfo> sdlsSecurityAssociations = new HashMap<>();

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
                // Save the SPI and its security association
                newSpis.add(spi);

                byte[] customAuthMask = null;
                if (saDef.containsKey("authMask")) {
                    customAuthMask = saDef.getBinary("authMask");
                }

                // Get custom Security Association args
                YConfiguration args = saDef.getConfig("args");
                // Find the desired SdlsSecurityAssociationProvider implementation
                ServiceLoader<SdlsSecurityAssociationFactory> loader =
                        ServiceLoader.load(SdlsSecurityAssociationFactory.class);
                Optional<ServiceLoader.Provider<SdlsSecurityAssociationFactory>> maybeSaImpl = loader.stream()
                        .filter(l -> l.get().getClass().getName().equals(saDef.get("class")))
                        .findFirst();
                if (maybeSaImpl.isEmpty()) {
                    throw new ConfigurationException("No implementation of SdlsSecurityAssociationFactory found for " + saDef.get("class"));
                }
                SdlsSecurityAssociationFactory saImpl = maybeSaImpl.get().get();
                // Create the Security Association
                SdlsSecurityAssociation sa = saImpl.create(yamcsInstance, linkName, spi, args);
                sdlsSecurityAssociations.put(spi, new SdlsInfo(sa, customAuthMask));
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