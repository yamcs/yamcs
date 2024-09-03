package org.yamcs.tctm.ccsds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.tctm.ccsds.VcDownlinkManagedParameters.TMDecoder;
import org.yamcs.time.Instant;
import org.yamcs.time.TimeService;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.YObjectLoader;

/**
 * Handles packets from one VC
 *
 * @author nm
 *
 */
public class VcTmPacketHandler implements TmPacketDataLink, VcDownlinkHandler {
    TmSink tmSink;
    private long numPackets;
    volatile boolean disabled = false;
    long lastFrameSeq = -1;
    EventProducer eventProducer;
    int packetLostCount;
    private final Log log;
    PacketDecoder packetDecoder;
    PixxelPacketDecoder pPacketDecoder;
    PixxelPacketMultipleDecoder pMultipleDecoder;
    long idleFrameCount = 0;
    PacketPreprocessor packetPreprocessor;
    final String name;
    final VcDownlinkManagedParameters vmp;

    AggregatedDataLink parent;
    private TimeService timeService;
    private Instant ertime;

    boolean isIdleVcid;

    public VcTmPacketHandler(String yamcsInstance, String name, VcDownlinkManagedParameters vmp) {
        this.vmp = vmp;
        this.name = name;
        timeService = YamcsServer.getTimeService(yamcsInstance);

        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        log = new Log(this.getClass(), yamcsInstance);
        log.setContext(name);

        // Temporary Packet Decoder
        pPacketDecoder = new PixxelPacketDecoder(vmp.maxPacketLength, p -> handlePacket(p));
        pMultipleDecoder = new PixxelPacketMultipleDecoder(vmp.maxPacketLength, p -> handlePacket(p));

        packetDecoder = new PacketDecoder(vmp.maxPacketLength, p -> handlePacket(p));
        packetDecoder.stripEncapsulationHeader(vmp.stripEncapsulationHeader);

        // Check if idle vcid?
        isIdleVcid = vmp.isIdleVcid;

        try {
            if (vmp.packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(vmp.packetPreprocessorClassName, yamcsInstance,
                        vmp.packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(vmp.packetPreprocessorClassName, yamcsInstance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        }
    }

    @Override
    public void handle(DownlinkTransferFrame frame) {
        if (disabled) {
            log.trace("Dropping frame for VC {} because the link is disabled", frame.getVirtualChannelId());
            return;
        }

        if (frame.containsOnlyIdleData()) {
            if (log.isTraceEnabled()) {
                log.trace("Dropping idle frame for VC {}, SEQ {}", frame.getVirtualChannelId(), frame.getVcFrameSeq());
            }
            lastFrameSeq = frame.getVcFrameSeq();
            idleFrameCount++;
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Processing frame VC {}, SEQ {}, FHP {}, DS {}, DE {}", frame.getVirtualChannelId(),
                    frame.getVcFrameSeq(),
                    frame.getFirstHeaderPointer(), frame.getDataStart(), frame.getDataEnd());
        }
        ertime = frame.getEarthRceptionTime();
        int dataStart = frame.getDataStart();
        int packetStart = frame.getFirstHeaderPointer();
        int dataEnd = frame.getDataEnd();
        byte[] data = frame.getData();

        if (vmp.tmDecoder == TMDecoder.CCSDS) {     // Multiple packets from frame | With Segmentation
            try {
                int frameLoss = frame.lostFramesCount(lastFrameSeq);
                lastFrameSeq = frame.getVcFrameSeq();

                if (packetDecoder.hasIncompletePacket()) {
                    if (frameLoss != 0) {
                        log.warn("Incomplete packet dropped because of frame loss ");
                        packetDecoder.reset();
                    } else {
                        if (packetStart != -1) {
                            packetDecoder.process(data, dataStart, packetStart - dataStart);
                        } else {
                            packetDecoder.process(data, dataStart, dataEnd - dataStart);
                        }
                    }
                }
                if (packetStart != -1) {
                    if (packetDecoder.hasIncompletePacket()) {
                        eventProducer
                                .sendWarning("Incomplete packet decoded when reaching the beginning of another packet");
                        packetDecoder.reset();
                    }
                    packetDecoder.process(data, packetStart, dataEnd - packetStart);
                }
            } catch (TcTmException e) {
                packetDecoder.reset();
                eventProducer.sendWarning(e.toString());
            }

        } else if (vmp.tmDecoder == TMDecoder.SINGLE) {     // Single packet per frame | No Segmentation
            try {   
                int frameLoss = frame.lostFramesCount(lastFrameSeq);
                lastFrameSeq = frame.getVcFrameSeq();

                if (frameLoss != 0) {
                    log.warn("Frame has been dropped, sigh");
                }

                if (packetStart != -1) {
                    pPacketDecoder.process(data, packetStart, dataEnd - packetStart);
                    pPacketDecoder.reset();
                }   
            } catch (TcTmException e) {
                pPacketDecoder.reset();
                eventProducer.sendWarning(e.toString());
            } catch (ArrayIndexOutOfBoundsException e) {
                pPacketDecoder.reset();
                log.warn(e.toString() + "\n"
                        + "     Full Frame: " + StringConverter.arrayToHexString(data, true) + "\n"
                        + "     Packet Start: " + packetStart + "\n"
                        + "     Data (i.e Frame) End: " + dataEnd + "\n"
                );
                eventProducer.sendWarning(e.toString());
            }
        } else {    // Multiple packets per frame | No segmentation
            try {
                int frameLoss = frame.lostFramesCount(lastFrameSeq);
                lastFrameSeq = frame.getVcFrameSeq();

                if (frameLoss != 0) {
                    log.warn("Frame has been dropped, sigh");
                }

                if (packetStart != -1) {
                    pMultipleDecoder.process(data, packetStart, dataEnd - packetStart);
                    pMultipleDecoder.reset();
                }   
            } catch (TcTmException e) {
                pMultipleDecoder.reset();

                List<String> params = List.of(
                    StringConverter.arrayToHexString(data, true),
                    Integer.toString(packetStart),
                    Integer.toString(dataEnd),
                    this.name
                );

                String message = "Full Frame: %s\n\nPacket Start: %s\n\nData (i.e Frame) End: %s\n\nLink: %s";
                log.logSentryFatal(e, message, getClass().getName(), params);

                eventProducer.sendWarning(e.toString());

            } catch (ArrayIndexOutOfBoundsException e) {
                pMultipleDecoder.reset();
                log.warn(e.toString() + "\n"
                        + "     Full Frame: " + StringConverter.arrayToHexString(data, true) + "\n"
                        + "     Packet Start: " + packetStart + "\n"
                        + "     Data (i.e Frame) End: " + dataEnd + "\n"
                );

                List<String> params = List.of(
                    StringConverter.arrayToHexString(data, true),
                    Integer.toString(packetStart),
                    Integer.toString(dataEnd),
                    this.name
                );
                String message = "Full Frame: %s\n\nPacket Start: %s\n\nData (i.e Frame) End: %s\n\nLink: %s";
                log.logSentryFatal(e, message, getClass().getName(), params);

                eventProducer.sendWarning(e.toString());

            } catch (Exception e) {
                pMultipleDecoder.reset();
                log.warn(e.toString() + "\n"
                        + "     Full Frame: " + StringConverter.arrayToHexString(data, true) + "\n"
                        + "     Packet Start: " + packetStart + "\n"
                        + "     Data (i.e Frame) End: " + dataEnd + "\n"
                );
                eventProducer.sendWarning(e.toString());

                List<String> params = List.of(
                    StringConverter.arrayToHexString(data, true),
                    Integer.toString(packetStart),
                    Integer.toString(dataEnd),
                    this.name
                );
                String message = "Full Frame: %s\n\nPacket Start: %s\n\nData (i.e Frame) End: %s\n\nLink: %s";
                log.logSentryFatal(e, message, getClass().getName(), params);
            }
        }
    }

    private void handlePacket(byte[] p) {
        if (log.isTraceEnabled()) {
            log.trace("VC {}, SEQ {} decoded packet of length {}", vmp.vcId, lastFrameSeq, p.length);
        }

        numPackets++;
        TmPacket pwt = new TmPacket(timeService.getMissionTime(), p);
        pwt.setEarthReceptionTime(ertime);

        pwt = packetPreprocessor.process(pwt);
        if (pwt != null) {
            tmSink.processPacket(pwt);
        }
    }

    @Override
    public Status getLinkStatus() {
        return disabled ? Status.DISABLED : Status.OK;
    }

    @Override
    public void enable() {
        this.disabled = false;
    }

    @Override
    public void disable() {
        this.disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return isIdleVcid? idleFrameCount: numPackets;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        numPackets = 0;
        idleFrameCount = 0;
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    public YConfiguration getConfig() {
        return vmp.config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AggregatedDataLink getParent() {
        return parent;
    }

    @Override
    public void setParent(AggregatedDataLink parent) {
        this.parent = parent;
    }

    @Override
    public Map<String, Object> getExtraInfo() {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("Idle CCSDS Frames", idleFrameCount);
        extra.put("Valid CCSDS Packets", numPackets);
        extra.put("Is Idle vcId link?", isIdleVcid);
        return extra;
    }

    /**
     * returns statistics with the number of datagram received and the number of
     * invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return "DISABLED";

        } else {
            return String.format("Idle CCSDS Frames: %d%n | Valid CCSDS Packets: %d%n", idleFrameCount, numPackets);
        }
    }

}
