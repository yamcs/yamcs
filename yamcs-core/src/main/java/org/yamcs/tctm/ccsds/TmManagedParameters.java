package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class TmManagedParameters extends DownlinkManagedParameters {
    int frameLength;
    int fshLength; // 0 means not present

    enum ServiceType {
        PACKET,
        /** Virtual Channel Access Service Data Unit */
        VCA
    };

    Map<Integer, TmVcManagedParameters> vcParams = new HashMap<>();

    public TmManagedParameters(YConfiguration config) {
        super(config);

        frameLength = config.getInt("frameLength");
        if (frameLength < 8 || frameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + frameLength);
        }

        if (errorDetection == FrameErrorDetection.CRC32) {
            throw new ConfigurationException("CRC32 not supported for TM frames");
        }

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            TmVcManagedParameters vmp = new TmVcManagedParameters(yc);
            if (vcParams.containsKey(vmp.vcId)) {
                throw new ConfigurationException("duplicate configuration of vcId " + vmp.vcId);
            }
            vcParams.put(vmp.vcId, vmp);
        }
    }

    @Override
    public int getMaxFrameLength() {
        return frameLength;
    }

    @Override
    public int getMinFrameLength() {
        return frameLength;
    }

    @Override
    public Map<Integer, VcDownlinkHandler> createVcHandlers(String yamcsInstance, String linkName) {
        Map<Integer, VcDownlinkHandler> m = new HashMap<>();
        for (Map.Entry<Integer, TmVcManagedParameters> me : vcParams.entrySet()) {
            TmVcManagedParameters vmp = me.getValue();
            switch (vmp.service) {
            case PACKET:
                VcTmPacketHandler vcph = new VcTmPacketHandler(yamcsInstance, linkName + ".vc" + vmp.vcId, vmp);
                m.put(vmp.vcId, vcph);
                break;
            case VCA:
                m.put(vmp.vcId, createVcaHandler(yamcsInstance, linkName, vmp));
                break;
            }
        }
        return m;
    }

    static class TmVcManagedParameters extends VcDownlinkManagedParameters {
        ServiceType service;

        public TmVcManagedParameters(YConfiguration config) {
            super(config);

            if (vcId < 0 || vcId > 7) {
                throw new ConfigurationException("Invalid vcId: " + vcId + ". Allowed values are from 0 to 7.");
            }
            service = config.getEnum("service", ServiceType.class);
            if (service == ServiceType.PACKET) {
                parsePacketConfig();
            } else if (service == ServiceType.VCA) {
                parseVcaConfig();
            }
        }
    }

}
