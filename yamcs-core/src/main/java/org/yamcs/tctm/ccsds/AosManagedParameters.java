package org.yamcs.tctm.ccsds;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.SdlsSecurityAssociation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AosManagedParameters extends DownlinkManagedParameters {
    enum ServiceType {
        /** Multiplexing Protocol Data Unit */
        PACKET,
        /** Bitstream Protocol Data Unit */
        // B_PDU,
        /** Virtual Channel Access Service Data Unit */
        VCA,
        /** IDLE frames are those with vcId = 63 */
        IDLE
    };

    final static int VCID_IDLE = 63;

    int frameLength;

    int insertZoneLength; // 0 means insert zone not present
    Map<Integer, AosVcManagedParameters> vcParams = new HashMap<>();

    public boolean frameHeaderErrorControlPresent;
    Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    public AosManagedParameters(YConfiguration config) {
        super(config);
        frameLength = config.getInt("frameLength");
        if (frameLength < 8 || frameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + frameLength);
        }
        if (errorDetection == FrameErrorDetection.CRC32) {
            throw new ConfigurationException("CRC32 not supported for AOS");
        }
        insertZoneLength = config.getInt("insertZoneLength", 0);

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
        if (config.containsKey("encryption")) {
            List<YConfiguration> encryptionConfigs = config.getConfigList("encryption");
            for (YConfiguration saDef : encryptionConfigs) {
                short spi = (short) saDef.getInt("spi");
                byte[] sdlsKey;
                try {
                    sdlsKey = Files.readAllBytes(Path.of(saDef.getString("keyFile")));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Create an auth mask for the primary header,
                // the frame data is already part of authentication.
                // No need to authenticate data, already part of GCM.
                // We never authenticate the optional insert zone
                // TODO: cite
                byte[] authMask = new byte[10];
                authMask[1] = 0b0011_1111; // authenticate only virtual channel ID

                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(sdlsKey, spi, authMask));
            }
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
        for (Map.Entry<Integer, AosVcManagedParameters> me : vcParams.entrySet()) {
            AosVcManagedParameters vmp = me.getValue();
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

    static class AosVcManagedParameters extends VcDownlinkManagedParameters {
        ServiceType service;
        boolean ocfPresent;
        short encryptionSpi;

        public AosVcManagedParameters(YConfiguration config) {
            super(config);
            if (config.containsKey("encryptionSpi")) {
                encryptionSpi = (short) config.getInt("encryptionSpi");
            }

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
            } else if (service == ServiceType.VCA) {
                parseVcaConfig();
            }
        }

        AosVcManagedParameters() {
            super(YConfiguration.emptyConfig());
        }
    }

    public VcDownlinkManagedParameters getVcParams(int vcId) {
        return vcParams.get(vcId);
    }

}
