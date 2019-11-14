package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
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

    public enum PriorityScheme {
        FIFO, ABSOLUTE, POLLING_VECTOR
    };

    PriorityScheme vcMultiplexingScheme;

    // make a frame out of multiple packets
    boolean blocking;

    List<TcVcManagedParameters> vcParams = new ArrayList<>();

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
            if(vmp.useCop1 && vcParams.stream().anyMatch(p -> p.useCop1 && p.vcId == vmp.vcId)) {
                throw new ConfigurationException("Cannot have two data links for the same vcId " + vmp.vcId+" and both using COP1");
            }
            if (vmp.maxFrameLength == -1) {
                vmp.maxFrameLength = maxFrameLength;
            }
            if(vmp.linkName == null) {
                vmp.linkName = "vc"+vmp.vcId;
                int c = 0;
                while (vcParams.stream().anyMatch(p->p.linkName.equals(vmp.linkName))) {
                    c++;
                    vmp.linkName = "vc"+vmp.vcId+"_"+c;
                }
            }
            
            
            vcParams.add(vmp);
        }
    }

    @Override
    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    @Override
    public List<VcUplinkHandler> createVcHandlers(String yamcsInstance, String parentLinkName,
            ScheduledThreadPoolExecutor executor) {
        List<VcUplinkHandler> l = new ArrayList<>();
        for (TcVcManagedParameters vmp : vcParams) {
            String linkName = parentLinkName + "."+vmp.linkName;
            switch (vmp.service) {
            case PACKET:
                VcUplinkHandler vcph;
                if (vmp.useCop1) {
                    vcph = new Cop1TcPacketHandler(yamcsInstance, linkName, vmp, executor,
                            new Cop1MonitorImpl(yamcsInstance, linkName));
                } else {
                    vcph = new TcPacketHandler(yamcsInstance, linkName, vmp);
                }
                l.add(vcph);
                break;
            case VCA_SDU:
                throw new UnsupportedOperationException("VCA_SDU not supported (TODO)");
            }
        }
        return l;
    }

    public class TcVcManagedParameters extends VcUplinkManagedParameters {
        
        ServiceType service;
        boolean useCop1;
        int maxFrameLength = -1;
        public boolean blocking;
        public boolean bdAbsolutePriority;
        //this is used to compose the link name, if not set it will be vc<x>
        String linkName;

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
            this.useCop1 = config.getBoolean("useCop1", false);
            this.linkName = config.getString("linkName", null);
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
