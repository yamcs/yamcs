package org.yamcs.tctm;

import java.io.FileNotFoundException;
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
 * @author nm
 *
 */
public class FileTmPacketProvider extends AbstractExecutionThreadService implements Runnable, TmPacketSource {
    volatile boolean quitting=false;
    EndAction endAction;
    String fileName;
    int fileoffset = 0;
    int packetcount = 0;
    long delayBetweenPackets=500;
    volatile boolean disabled=false;
    TmSink tmSink;
    TmFileReader tmFileReader;
    long tmCount=0;

    static Logger log=LoggerFactory.getLogger(FileTmPacketProvider.class.getName());
    TimeService timeService;
    
    public FileTmPacketProvider(String instance, String name, String fileName) throws FileNotFoundException {
        this(fileName, "STOP",1000);
        timeService = YamcsServer.getTimeService(instance);
    }

    /**
     * Constructs a packet provider that sends packets from a file at the indicated speed. 
     * If the parameter loop is set to true, then jump back at the beginning of the file once the end has been reached.
     * @param fileName
     * @param delayBetweenPackets
     * @throws FileNotFoundException
     */
    public FileTmPacketProvider(String fileName, String endActionStr, long delayBetweenPackets) throws FileNotFoundException {
        this.fileName = fileName;
        this.delayBetweenPackets=delayBetweenPackets;
        if (endActionStr.equalsIgnoreCase("LOOP")) endAction=EndAction.LOOP;
        else if (endActionStr.equalsIgnoreCase("QUIT")) endAction=EndAction.QUIT;
        else if (endActionStr.equalsIgnoreCase("STOP")) endAction=EndAction.STOP;
        log.debug("attempting to open file " + this.fileName);
        tmFileReader = new TmFileReader(this.fileName);
        timeService = new RealtimeTimeService();
    }

    @Override
    public void setTmSink(TmSink tmProcessor) {
        this.tmSink=tmProcessor;
    }

    @Override
    public void run() {
        try {
            while(isRunning()) {
                while(disabled) {
                    Thread.sleep(1000);
                }
                PacketWithTime pwrt;
                pwrt = tmFileReader.readPacket(timeService.getMissionTime());

                if(pwrt==null) {
                    if ( endAction==EndAction.LOOP ) {
                        log.info("File " + fileName + " finished, looping back to the beginning");
                        tmFileReader = new TmFileReader(this.fileName);
                    } else {
                        log.info("File " + fileName + " finished");
                        break;
                    }
                }
                if(delayBetweenPackets>0) {
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

    @Override
    public boolean isArchiveReplay() {
        return true;
    }
    /**
     * @param delayBetweenPackets the delayBetweenPakets to set
     */
    public void setDelayBetweenPackets(int delayBetweenPackets) {
        this.delayBetweenPackets = delayBetweenPackets;
    }

    @Override
    public String getLinkStatus() {
        if (disabled) return "DISABLED";
        if(quitting) {
            return "UNAVAIL";
        } else {
            return "OK";
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
        disabled=true;
    }
    @Override
    public void enable() {
        disabled=false;
    }
    @Override
    public boolean isDisabled() {
        return disabled;
    }
    @Override
    public String getDetailedStatus() {
        return "Playing file "+fileName+", endAction="+endAction+" delayBetweenPackets="+delayBetweenPackets+" packetcount="+packetcount;
    }

    @Override
    public long getDataCount() {
        return tmCount;
    }
}
