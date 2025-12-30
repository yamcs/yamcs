package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.memento.MementoDb;
import org.yamcs.security.sdls.SdlsMemento;
import org.yamcs.security.sdls.SdlsSecurityAssociationFactory;
import org.yamcs.security.sdls.SdlsSecurityAssociation;
import org.yamcs.tctm.Link;
import org.yamcs.utils.YObjectLoader;

/**
 * Stores configuration related to Master channels for downlink.
 *
 */
public abstract class DownlinkManagedParameters {
    public enum FrameErrorDetection {
        NONE, CRC16, CRC32
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
    protected record SdlsInfo(SdlsSecurityAssociation sa, byte[] customAuthMask) {
        public SdlsInfo {
            Objects.requireNonNull(sa);
        }
    }

    /**
     * A map of Security Parameter Indices to Security Associations
     */
    final Map<Short, SdlsInfo> sdlsSecurityAssociations = new HashMap<>();

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

                // Grab the auth mask from the config, if we have one
                byte[] customAuthMask = null;
                if (saDef.containsKey("authMask")) {
                    customAuthMask = saDef.getBinary("authMask");
                }
                // Get custom Security Association args
                YConfiguration args = saDef.getConfig("args");
                // Find the desired SdlsSecurityAssociationProvider implementation
                ServiceLoader<SdlsSecurityAssociationFactory> loader = ServiceLoader.load(SdlsSecurityAssociationFactory.class);
                Optional<ServiceLoader.Provider<SdlsSecurityAssociationFactory>> maybeSaImpl = loader.stream()
                        .filter(l -> l.get().getClass().getName().equals(saDef.get("class")))
                        .findFirst();
                if (maybeSaImpl.isEmpty()) {
                    throw new ConfigurationException("No implementation of SdlsSecurityAssociationFactory found for " + saDef.get("class"));
                }
                SdlsSecurityAssociationFactory saImpl = maybeSaImpl.get().get();
                // Create the Security Association
                SdlsSecurityAssociation sa = saImpl.create(yamcsInstance, linkName, spi, args);

                // Save the SPI and its security association + optional auth mask
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