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

public class TmManagedParameters extends DownlinkManagedParameters {
    int frameLength;
    int fshLength; // 0 means not present
    Map<Short, SdlsSecurityAssociation> sdlsSecurityAssociations = new HashMap<>();

    enum ServiceType {
        PACKET,
        /** Virtual Channel Access Service Data Unit */
        VCA
    };

    Map<Integer, TmVcManagedParameters> vcParams = new HashMap<>();

    public TmManagedParameters(YConfiguration config) {
        super(config);

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
                // No need to authenticate data, already part of GCM
                byte[] authMask = new byte[6];
                authMask[1] = 0b0000_1110; // authenticate virtual channel ID

                sdlsSecurityAssociations.put(spi, new SdlsSecurityAssociation(sdlsKey, spi, authMask));
            }
        }

        frameLength = config.getInt("frameLength");
        if (frameLength < 8 || frameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + frameLength);
        }

        if (errorDetection == FrameErrorDetection.CRC32) {
            throw new ConfigurationException("CRC32 not supported for TM frames");
        }

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            TmVcManagedParameters vmp = new TmVcManagedParameters(yc, this);
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
        short encryptionSpi;

        public TmVcManagedParameters(YConfiguration config, TmManagedParameters tmParams) {
            super(config);
            if (config.containsKey("encryptionSpi")) {
                encryptionSpi = (short) config.getInt("encryptionSpi");
                if (!tmParams.sdlsSecurityAssociations.containsKey(encryptionSpi)) {
                    throw new ConfigurationException("Encryption SPI " + encryptionSpi
                            + " configured for vcId "
                            + vcId + " is not configured for link " + config.getString("linkName"));
                }
            }
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
