package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class UslpManagedParameters extends DownlinkManagedParameters {

    enum COPType {
        COP_1, COP_P, NONE
    };

    enum ServiceType {
        /** Multiplexing Protocol Data Unit */
        PACKET,
        /** IDLE frames are those with vcId = 63 */
        IDLE,
        /** Virtual Channel Access */
        VCA
    };

    int frameLength; // frame length if fixed or -1 if not fixed
    int maxFrameLength;
    int minFrameLength;

    int insertZoneLength; // 0 means not present
    boolean generateOidFrame;

    int fshLength; // 0 means not present
    Map<Integer, UslpVcManagedParameters> vcParams = new HashMap<>();

    public UslpManagedParameters(YConfiguration config) {
        super(config);

        frameLength = config.getInt("frameLength", -1);
        if (frameLength < 0) {
            maxFrameLength = config.getInt("maxFrameLength", 65535);
            minFrameLength = config.getInt("minFrameLength", 6);
        } else {
            maxFrameLength = frameLength;
            minFrameLength = frameLength;
        }

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            UslpVcManagedParameters ump = new UslpVcManagedParameters(yc);
            if (vcParams.containsKey(ump.vcId)) {
                throw new ConfigurationException("duplicate configuration of vcId " + ump.vcId);
            }
            vcParams.put(ump.vcId, ump);
        }

        insertZoneLength = config.getInt("insertZoneLength", 0);
        if (insertZoneLength < 0 || insertZoneLength > minFrameLength - 6) {
            throw new ConfigurationException("Invalid insert zone length " + insertZoneLength);
        }
    }

    static class UslpVcManagedParameters extends VcDownlinkManagedParameters {
        ServiceType service;

        COPType copInEffect;
        boolean fixedLength; // or variable length
        int vcCountLengthForSeqControlQos;
        int vcCountLengthForExpeditedQos;
        int truncatedTransferFrameLength;

        public UslpVcManagedParameters(YConfiguration config) {
            super(config);
            service = config.getEnum("service", ServiceType.class);
            if (service == ServiceType.PACKET) {
                parsePacketConfig();
            } else if (service == ServiceType.VCA) {
                parseVcaConfig();
            }
        }

    }

    static class MapManagedParameters {
        int mapId;
        int maxPacketLength;
    }

    @Override
    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    @Override
    public int getMinFrameLength() {
        return minFrameLength;
    }

    @Override
    public Map<Integer, VcDownlinkHandler> createVcHandlers(String yamcsInstance, String linkName) {
        Map<Integer, VcDownlinkHandler> m = new HashMap<>();
        for (Map.Entry<Integer, UslpVcManagedParameters> me : vcParams.entrySet()) {
            UslpVcManagedParameters vmp = me.getValue();
            switch (vmp.service) {
            case PACKET:
                VcTmPacketHandler vcph = new VcTmPacketHandler(yamcsInstance,
                        linkName + ".vc" + vmp.vcId, vmp);
                m.put(vmp.vcId, vcph);
                break;
            case IDLE:
                m.put(vmp.vcId, new IdleFrameHandler());
                break;
            case VCA:
                m.put(vmp.vcId, createVcaHandler(yamcsInstance, linkName, vmp));
                break;

            default:
                throw new UnsupportedOperationException(vmp.service + " not supported (TODO)");
            }
        }
        return m;
    }
}
