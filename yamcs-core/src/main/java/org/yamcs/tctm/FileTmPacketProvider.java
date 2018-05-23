package org.yamcs.tctm;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Plays files in pacts format, hrdp format or containing raw ccsds packets.
 * 
 * @author nm
 *
 */
public class FileTmPacketProvider extends AbstractExecutionThreadService implements Runnable, TmPacketDataLink {
    volatile boolean quitting = false;
    EndAction endAction;
    String fileName;
    int fileoffset = 0;
    int packetcount = 0;
    long delayBetweenPackets = 500;
    volatile boolean disabled = false;
    TmSink tmSink;
    TmFileReader tmFileReader;
    long tmCount = 0;

    static Logger log = LoggerFactory.getLogger(FileTmPacketProvider.class.getName());
    TimeService timeService;

    public FileTmPacketProvider(String instance, String name, String fileName) throws IOException {
        this(fileName, "STOP", 1000);
        timeService = YamcsServer.getTimeService(instance);
    }

    /**
     * Constructs a packet provider that sends packets from a file at the indicated speed.
     * If the parameter loop is set to true, then jump back at the beginning of the file once the end has been reached.
     * 
     * @param fileName
     * @param delayBetweenPackets
     * @throws IOException
     */
    public FileTmPacketProvider(String fileName, String endActionStr, long delayBetweenPackets) throws IOException {
        this.fileName = fileName;
        this.delayBetweenPackets = delayBetweenPackets;
        if (endActionStr.equalsIgnoreCase("LOOP")) {
            endAction = EndAction.LOOP;
        } else if (endActionStr.equalsIgnoreCase("QUIT")) {
            endAction = EndAction.QUIT;
        } else if (endActionStr.equalsIgnoreCase("STOP")) {
            endAction = EndAction.STOP;
        }
        log.debug("attempting to open file {}", this.fileName);
        tmFileReader = new TmFileReader(this.fileName, new IssPacketPreprocessor(null));
        timeService = new RealtimeTimeService();
    }

    @Override
    public void setTmSink(TmSink tmProcessor) {
        this.tmSink = tmProcessor;
    }

    @Override
    public void run() {
        try {
            while (isRunning()) {
                while (disabled) {
                    Thread.sleep(1000);
                }
                PacketWithTime pwrt;
                pwrt = tmFileReader.readPacket(timeService.getMissionTime());

                if (pwrt == null) {
                    if (endAction == EndAction.LOOP) {
                        log.info("File {} finished, looping back to the beginning", fileName);
                        tmFileReader = new TmFileReader(this.fileName, new IssPacketPreprocessor(null));
                    } else {
                        log.info("File {} finished", fileName);
                        break;
                    }
                }
                if (delayBetweenPackets > 0) {
                    Thread.sleep(delayBetweenPackets);
                }
                tmSink.processPacket(pwrt);
                tmCount++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.warn("Got exception while reading packets: ", e);
        }
    }

    /**
     * @return the delayBetweenPakets
     */
    public long getDelayBetweenPackets() {
        return delayBetweenPackets;
    }

    /**
     * @param delayBetweenPackets
     *            the delayBetweenPakets to set
     */
    public void setDelayBetweenPackets(int delayBetweenPackets) {
        this.delayBetweenPackets = delayBetweenPackets;
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (quitting) {
            return Status.UNAVAIL;
        } else {
            return Status.OK;
        }
    }

    @Override
    public void triggerShutdown() {
        try {
            tmFileReader.close();
        } catch (IOException e) {
            log.warn("Got exception while closing the stream: ", e);
        }
    }

    @Override
    public void disable() {
        disabled = true;
    }

    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getDetailedStatus() {
        return "Playing file " + fileName + ", endAction=" + endAction + " delayBetweenPackets=" + delayBetweenPackets
                + " packetcount=" + packetcount;
    }

    @Override
    public long getDataCount() {
        return tmCount;
    }
}
