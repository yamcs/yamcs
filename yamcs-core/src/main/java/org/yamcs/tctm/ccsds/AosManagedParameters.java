package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class AosManagedParameters extends ManagedParameters {
    enum ServiceType {
        /** Multiplexing Protocol Data Unit */
        PACKET,
        /** Bitstream Protocol Data Unit */
        // B_PDU,
        /** Virtual Channel Access Service Data Unit */
        // VCA_SDU,
        /** IDLE frames are those with vcId = 63 */
        IDLE
    };

    final static int VCID_IDLE = 63;

    int frameLength;

    int insertZoneLength; // 0 means insert zone not present
    Map<Integer, AosVcManagedParameters> vcParams = new HashMap<>();

    public boolean frameHeaderErrorControlPresent;

    public AosManagedParameters(YConfiguration config) {

        if (config.containsKey("physicalChannelName")) {
            physicalChannelName = config.getString("physicalChannelName");
        }
        frameLength = config.getInt("frameLength");
        if (frameLength < 8 || frameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + frameLength);
        }
        errorCorrection = config.getEnum("errorCorrection", FrameErrorCorrection.class);
        if(errorCorrection == FrameErrorCorrection.CRC32) {
            throw new ConfigurationException("CRC32 not supported for AOS");
        }
        insertZoneLength = config.getInt("insertZoneLength");

        if (insertZoneLength < 0 || insertZoneLength > frameLength - 6) {
            throw new ConfigurationException("Invalid insert zone length " + insertZoneLength);
        }
        
        frameHeaderErrorControlPresent = config.getBoolean("frameHeaderErrorControlPresent");

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            AosVcManagedParameters vmp = new AosVcManagedParameters(yc);
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
    public Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance, String linkName) {
        Map<Integer, VirtualChannelHandler> m = new HashMap<>();
        for (Map.Entry<Integer, AosVcManagedParameters> me : vcParams.entrySet()) {
            AosVcManagedParameters vmp = me.getValue();
            switch (vmp.service) {
            case PACKET:
                VirtualChannelPacketHandler vcph = new VirtualChannelPacketHandler(yamcsInstance,
                        linkName + ".vc" + vmp.vcId, vmp);
                m.put(vmp.vcId, vcph);
                break;
            case IDLE:
                m.put(vmp.vcId, new IdleFrameHandler());
                break;
            default:
                throw new UnsupportedOperationException(vmp.service + " not supported (TODO)");
            }
        }
        return m;
    }

    static class AosVcManagedParameters extends VcManagedParameters {
        ServiceType service;
        boolean ocfPresent;
        
        public AosVcManagedParameters(YConfiguration config) {
            super(config);

            if (vcId < 0 || vcId > 63) {
                throw new ConfigurationException("Invalid vcId: " + vcId + ". Allowed values are from 0 to 63.");
            }
            service = config.getEnum("service", ServiceType.class);
            if (vcId == VCID_IDLE && service != ServiceType.IDLE) {
                throw new ConfigurationException(
                        "vcid " + VCID_IDLE + " is reserved for IDLE frames (please set service: IDLE)");
            }

            ocfPresent = config.getBoolean("ocfPresent");
            if (service == ServiceType.PACKET) {
                parsePacketConfig();
            }
        }

        AosVcManagedParameters() {
            super(YConfiguration.emptyConfig());
        }
    }

    public VcManagedParameters getVcParams(int vcId) {
        return vcParams.get(vcId);
    }

}
