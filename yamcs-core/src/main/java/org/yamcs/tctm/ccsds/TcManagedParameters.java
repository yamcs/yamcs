package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Parameters used for generation of TC frames as per CCSDS 232.0-B-3
 * 
 * @author nm
 *
 */
public class TcManagedParameters extends UplinkManagedParameters {
    int maxFrameLength;

    public enum MultiplexingScheme {
        FIFO, ABSOLUTE_PRIORITY, POLLING_VECTOR
    };

    MultiplexingScheme vcMultiplexingScheme;

    // make a frame out of multiple packets
    boolean blocking;

    Map<Integer, TcVcManagedParameters> vcParams = new HashMap<>();

    public TcManagedParameters(YConfiguration config) {
        super(config);
        maxFrameLength = config.getInt("maxFrameLength");
        blocking = config.getBoolean("blocking", true);

        if (maxFrameLength < 8 || maxFrameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + maxFrameLength);
        }

        errorCorrection = config.getEnum("errorCorrection", FrameErrorCorrection.class, FrameErrorCorrection.CRC16);
        if (errorCorrection == FrameErrorCorrection.CRC32) {
            throw new ConfigurationException("CRC32 not supported for TC frames");
        }

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            TcVcManagedParameters vmp = new TcVcManagedParameters(yc, this);
            if (vcParams.containsKey(vmp.vcId)) {
                throw new ConfigurationException("duplicate configuration of vcId " + vmp.vcId);
            }
            if (vmp.maxFrameLength == -1) {
                vmp.maxFrameLength = maxFrameLength;
            }
            vcParams.put(vmp.vcId, vmp);
        }
    }

    @Override
    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    @Override
    public Map<Integer, VcUplinkHandler> createVcHandlers(String yamcsInstance, String parentLinkName,
            ScheduledThreadPoolExecutor executor) {
        Map<Integer, VcUplinkHandler> m = new HashMap<>();
        for (Map.Entry<Integer, TcVcManagedParameters> me : vcParams.entrySet()) {
            TcVcManagedParameters vmp = me.getValue();
            String linkName = parentLinkName + ".vc" + vmp.vcId;
            switch (vmp.service) {
            case PACKET:
                VcUplinkHandler vcph;
                if (vmp.useFop1) {
                    vcph = new Cop1TcPacketHandler(yamcsInstance, linkName, vmp, executor,
                            new Cop1MonitorImpl(yamcsInstance, linkName));
                } else {
                    vcph = new TcPacketHandler(yamcsInstance, linkName, vmp);
                }
                m.put(vmp.vcId, vcph);
                break;
            case VCA_SDU:
                throw new UnsupportedOperationException("VCA_SDU not supported (TODO)");
            }
        }
        return m;
    }

    public class TcVcManagedParameters extends VcUplinkManagedParameters {
        ServiceType service;
        boolean useFop1;
        int maxFrameLength = -1;
        public boolean blocking;
        public boolean bdAbsolutePriority;

        public TcVcManagedParameters(YConfiguration config, TcManagedParameters tmp) {
            super(config);

            if (vcId < 0 || vcId > 7) {
                throw new ConfigurationException("Invalid vcId: " + vcId + ". Allowed values are from 0 to 7.");
            }
            service = config.getEnum("service", ServiceType.class, ServiceType.PACKET);

            maxFrameLength = config.getInt("maxFrameLength", tmp.maxFrameLength);
            if (maxFrameLength < 8 || maxFrameLength > 0xFFFF) {
                throw new ConfigurationException("Invalid frame length " + maxFrameLength);
            }
            this.bdAbsolutePriority = config.getBoolean("bdAbsolutePriority", false);
            this.useFop1 = config.getBoolean("useFop1", false);
        }

        public TcVcManagedParameters(int vcId, ServiceType service) {
            super(vcId);
            this.service = service;
        }

        public TcFrameFactory getFrameFactory() {
            return new TcFrameFactory(TcManagedParameters.this);
        }
    }

    TcVcManagedParameters getVcParams(int vcId) {
        return vcParams.get(vcId);
    }
}
