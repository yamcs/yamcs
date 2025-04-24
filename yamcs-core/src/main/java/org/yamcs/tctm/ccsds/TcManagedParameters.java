package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Configuration (managed parameters) used for generation of TC frames as per CCSDS 232.0-B-3
 * 
 */
public class TcManagedParameters extends UplinkManagedParameters {
    int maxFrameLength;

    public enum PriorityScheme {
        FIFO, ABSOLUTE, POLLING_VECTOR
    };

    PriorityScheme priorityScheme;

    List<TcVcManagedParameters> vcParams = new ArrayList<>();

    public TcManagedParameters(YConfiguration config) {
        super(config);
        maxFrameLength = config.getInt("maxFrameLength");

        if (maxFrameLength < 8 || maxFrameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + maxFrameLength);
        }
        if (errorDetection == FrameErrorDetection.CRC32) {
            throw new ConfigurationException("CRC32 not supported for TC frames");
        }

        priorityScheme = config.getEnum("priorityScheme", PriorityScheme.class, PriorityScheme.FIFO);

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            TcVcManagedParameters vmp = new TcVcManagedParameters(yc, this);
            if (vmp.useCop1 && vcParams.stream().anyMatch(p -> p.useCop1 && p.vcId == vmp.vcId)) {
                throw new ConfigurationException(
                        "Cannot have two data links for the same vcId " + vmp.vcId + " and both using COP1");
            }
            if (vmp.maxFrameLength == -1) {
                vmp.maxFrameLength = maxFrameLength;
            }
            if (vmp.linkName == null) {
                vmp.linkName = "vc" + vmp.vcId;
                int c = 0;
                while (vcParams.stream().anyMatch(p -> p.linkName.equals(vmp.linkName))) {
                    c++;
                    vmp.linkName = "vc" + vmp.vcId + "_" + c;
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
            String linkName = parentLinkName + "." + vmp.linkName;
            switch (vmp.service) {
            case PACKET:
                VcUplinkHandler vcph;
                if (vmp.useCop1) {
                    vcph = new Cop1TcPacketHandler(yamcsInstance, linkName, vmp, executor);
                    ((Cop1TcPacketHandler) vcph).addMonitor(new Cop1MonitorImpl(yamcsInstance, linkName));
                } else {
                    vcph = new TcPacketHandler(yamcsInstance, linkName, vmp);
                }
                l.add(vcph);
                break;
            }
        }
        return l;
    }

    TcVcManagedParameters getVcParams(int vcId) {
        for (var vcp : vcParams) {
            if (vcp.vcId == vcId) {
                return vcp;
            }
        }
        return null;
    }

    /**
     * Get the frame error detection in use
     */
    public FrameErrorDetection getErrorDetection() {
        return errorDetection;
    }

    /**
     * Configuration for one Virtual Channel
     *
     */
    static public class TcVcManagedParameters extends VcUplinkManagedParameters {
        final TcManagedParameters tcParams;
        /**
         * Allows to enable/disable frame error detection at Virtual Channel level.
         * <p>
         * This is not according to CCSDS standard which specifies that this shall be done at the level of physical
         * channel.
         * <p>
         * This can be null, unlike the field from {@link UplinkManagedParameters#errorDetection} which is none if no
         * error detection is used. Null means that the error detection at link level is being used.
         */
        FrameErrorDetection errorDetection;

        ServiceType service;
        boolean useCop1;
        int maxFrameLength;
        public boolean multiplePacketsPerFrame;
        public boolean bdAbsolutePriority;

        // if not negative, it contains the default MAP_ID to be used for this virtual channel
        // if negative, this virtual channel does not use the MAP service
        final byte mapId;

        // this is used to compose the link name, if not set it will be vc<x>
        String linkName;

        public TcVcManagedParameters(YConfiguration config, TcManagedParameters tcParams) {
            super(config, tcParams);
            this.tcParams = tcParams;
            this.errorDetection = config.getEnum("errorDetection", FrameErrorDetection.class,
                    null);

            if (vcId < 0 || vcId > 63) {
                throw new ConfigurationException("Invalid vcId: " + vcId + ". Allowed values are from 0 to 63.");
            }
            service = config.getEnum("service", ServiceType.class, ServiceType.PACKET);

            maxFrameLength = config.getInt("maxFrameLength", tcParams.maxFrameLength);
            if (maxFrameLength < 8) {
                throw new ConfigurationException("Invalid frame length " + maxFrameLength);
            }
            if (maxFrameLength > tcParams.maxFrameLength) {
                throw new ConfigurationException("Invalid frame length " + maxFrameLength
                        + " has to be at most equal to the master channel max length " + tcParams.maxFrameLength);
            }

            this.bdAbsolutePriority = config.getBoolean("bdAbsolutePriority", false);
            this.useCop1 = config.getBoolean("useCop1", false);
            this.linkName = config.getString("linkName", null);
            this.multiplePacketsPerFrame = config.getBoolean("multiplePacketsPerFrame", true);
            this.mapId = (byte) config.getInt("mapId", -1);
            if (mapId < -1 || mapId > 15) {
                throw new ConfigurationException("Invalid mapId " + mapId
                        + ". It has to be either -1 (meaning that the MAP service is not used) or between 0 and 15");
            }

            int authMaskSize = 5;
            if (mapId >= 0)
                authMaskSize += 1;

            // No need to authenticate data, already part of GCM
            // (source: McGrew and Viega, "The Galois/Counter Mode of Operation (GCM)").
            // Create an auth mask for the primary header, according to CCSDS Standard for
            // Space Data Link Security (CCSDS 355.0-B-2).
            // The SDLS implementation automatically adds the security header to authenticated data.
            authMask = new byte[authMaskSize];
            authMask[2] = (byte) 0b1111_1100; // authenticate virtual channel ID
            if (mapId >= 0)
                authMask[4] = (byte) 0xff; // authenticate segment header
        }

        public TcFrameFactory getFrameFactory() {
            return new TcFrameFactory(this);
        }

        /**
         * Returns the error detection used for this virtual channel.
         */
        public FrameErrorDetection getErrorDetection() {
            return errorDetection == null ? tcParams.getErrorDetection() : errorDetection;
        }
    }
}