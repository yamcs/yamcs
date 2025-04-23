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
    Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

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

                // Authenticate virtual channel ID and MAP ID
                byte[] authMask = new byte[14];
                authMask[2] = 0b111; // top 3 bits of vcid
                authMask[3] = (byte) 0b1111_1110; // bottom 3 bits of vcid, 4 bits of map id

                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(sdlsKey, spi, authMask));
            }
        }
    }

    static class UslpVcManagedParameters extends VcDownlinkManagedParameters {
        ServiceType service;

        COPType copInEffect;
        boolean fixedLength; // or variable length
        int vcCountLengthForSeqControlQos;
        int vcCountLengthForExpeditedQos;
        int truncatedTransferFrameLength;
        short encryptionSpi;

        public UslpVcManagedParameters(YConfiguration config) {
            super(config);
            if (config.containsKey("encryptionSpi")) {
                encryptionSpi = (short) config.getInt("encryptionSpi");
            }
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
