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
public class TcManagedParameters extends UplinkManagedParameters<TcTransferFrame> {
    int maxFrameLength;


    List<TcVcManagedParameters> vcParams = new ArrayList<>();

    public TcManagedParameters(YConfiguration config, String yamcsInstance, String linkName) {
        super(config, yamcsInstance, linkName);
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
    public List<VcUplinkHandler<TcTransferFrame>> createVcHandlers(String yamcsInstance, String parentLinkName,
            ScheduledThreadPoolExecutor executor) {
        List<VcUplinkHandler<TcTransferFrame>> l = new ArrayList<>();
        for (TcVcManagedParameters vmp : vcParams) {
            String linkName = parentLinkName + "." + vmp.linkName;
            switch (vmp.service) {
            case PACKET:
                VcUplinkHandler<TcTransferFrame> vcph;
                if (vmp.useCop1) {
                    var cop1Handler = new Cop1UplinkPacketHandler<TcTransferFrame>(yamcsInstance, linkName, vmp, executor);
                    cop1Handler.addMonitor(new Cop1MonitorImpl(yamcsInstance, linkName));
                    vcph = cop1Handler;
                } else {
                    vcph = new UplinkPacketHandler<TcTransferFrame>(yamcsInstance, linkName, vmp);
                }
                l.add(vcph);
                break;
            }
        }
        return l;
    }

    public TcVcManagedParameters getVcParams(int vcId) {
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

    @Override
    public String toString() {
        return "TcManagedParameters [maxFrameLength=" + maxFrameLength + ", vcParams=" + vcParams + ", priorityScheme="
                + priorityScheme + ", physicalChannelName=" + physicalChannelName + ", spacecraftId=" + spacecraftId
                + ", errorDetection=" + errorDetection + ", linkName=" + linkName + ", sdlsSecurityAssociations="
                + sdlsSecurityAssociations + "]";
    }

    /**
     * Configuration for one Virtual Channel
     *
     */
    static public class TcVcManagedParameters extends VcUplinkManagedParameters<TcTransferFrame> {
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

        int maxFrameLength;


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


            maxFrameLength = config.getInt("maxFrameLength", tcParams.maxFrameLength);
            if (maxFrameLength < 8) {
                throw new ConfigurationException("Invalid frame length " + maxFrameLength);
            }
            if (maxFrameLength > tcParams.maxFrameLength) {
                throw new ConfigurationException("Invalid frame length " + maxFrameLength
                        + " has to be at most equal to the master channel max length " + tcParams.maxFrameLength);
            }

            this.mapId = (byte) config.getInt("mapId", -1);
            if (mapId < -1 || mapId > 15) {
                throw new ConfigurationException("Invalid mapId " + mapId
                        + ". It has to be either -1 (meaning that the MAP service is not used) or between 0 and 15");
            }
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

        @Override
        public int getMaxFrameLength() {
            return maxFrameLength;
        }
    }
}