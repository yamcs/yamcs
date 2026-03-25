package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Configuration for USLP uplink (master channel and virtual channels) as per CCSDS 732.1-B-3.
 */
public class UslpUplinkManagedParameters extends UplinkManagedParameters<UslpUplinkTransferFrame> {
    int maxFrameLength;
    int insertZoneLength;

    List<UslpUplinkVcManagedParameters> vcParams = new ArrayList<>();

    public UslpUplinkManagedParameters(YConfiguration config, String yamcsInstance, String linkName) {
        super(config, yamcsInstance, linkName);

        maxFrameLength = config.getInt("maxFrameLength", 65535);
        if (maxFrameLength < 8 || maxFrameLength > 65535) {
            throw new ConfigurationException("Invalid maxFrameLength " + maxFrameLength);
        }

        insertZoneLength = config.getInt("insertZoneLength", 0);
        if (insertZoneLength < 0) {
            throw new ConfigurationException("Invalid insertZoneLength " + insertZoneLength);
        }

        priorityScheme = config.getEnum("priorityScheme", PriorityScheme.class, PriorityScheme.FIFO);

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            UslpUplinkVcManagedParameters vmp = new UslpUplinkVcManagedParameters(yc, this);
            if (vcParams.stream().anyMatch(p -> p.vcId == vmp.vcId)) {
                throw new ConfigurationException("Duplicate vcId " + vmp.vcId);
            }
            vcParams.add(vmp);
        }
    }

    @Override
    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    @Override
    public List<VcUplinkHandler<UslpUplinkTransferFrame>> createVcHandlers(String yamcsInstance,
            String parentLinkName, ScheduledThreadPoolExecutor executor) {
        List<VcUplinkHandler<UslpUplinkTransferFrame>> l = new ArrayList<>();
        for (UslpUplinkVcManagedParameters vmp : vcParams) {
            String linkName = parentLinkName + "." + vmp.linkName();
            l.add(new UplinkPacketHandler<>(yamcsInstance, linkName, vmp));
        }
        return l;
    }

    @Override
    public UslpUplinkVcManagedParameters getVcParams(int vcId) {
        for (var vmp : vcParams) {
            if (vmp.vcId == vcId) {
                return vmp;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "UslpUplinkManagedParameters [maxFrameLength=" + maxFrameLength + ", insertZoneLength="
                + insertZoneLength + ", vcParams=" + vcParams + ", priorityScheme=" + priorityScheme
                + ", physicalChannelName=" + physicalChannelName + ", spacecraftId=" + spacecraftId
                + ", errorDetection=" + errorDetection + ", linkName=" + linkName + ", sdlsSecurityAssociations="
                + sdlsSecurityAssociations + "]";
    }

    public static class UslpUplinkVcManagedParameters extends VcUplinkManagedParameters<UslpUplinkTransferFrame> {
        final UslpUplinkManagedParameters uslpParams;
        /**
         * Number of bytes used for the VC Frame Count field in the primary header (0–7).
         * 0 means no sequence count is included.
         */
        final int vcfCountLength;
        int maxFrameLength;


        public UslpUplinkVcManagedParameters(YConfiguration config, UslpUplinkManagedParameters uslpParams) {
            super(config, uslpParams);
            this.uslpParams = uslpParams;

            if (vcId < 0 || vcId > 62) {
                throw new ConfigurationException("Invalid vcId " + vcId + "; allowed range is 0–62");
            }

            vcfCountLength = config.getInt("vcfCountLength", 0);
            if (vcfCountLength < 0 || vcfCountLength > 7) {
                throw new ConfigurationException("Invalid vcfCountLength " + vcfCountLength + "; must be 0–7");
            }

            maxFrameLength = config.getInt("maxFrameLength", uslpParams.maxFrameLength);
            if (maxFrameLength > uslpParams.maxFrameLength) {
                throw new ConfigurationException("VC maxFrameLength " + maxFrameLength
                        + " exceeds master channel maxFrameLength " + uslpParams.maxFrameLength);
            }


        }

        String linkName() {
            return vcLinkName;
        }

        @Override
        public int getMaxFrameLength() {
            return maxFrameLength;
        }

        @Override
        public UplinkFrameFactory<UslpUplinkTransferFrame> getFrameFactory() {
            return new UslpUplinkFrameFactory(this);
        }
    }
}
