package org.yamcs.tctm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.TmProcessor;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.time.TimeService;

import com.google.common.util.concurrent.AbstractExecutionThreadService;


/**
 * Reads telemetry files from the directory yamcs.incomingDir/tm
 * @author mache
 *
 */
public class FilePollingTmDataLink extends AbstractExecutionThreadService implements TmPacketDataLink {
    final String incomingDir;
    final private Logger log;
    volatile boolean disabled;
    TmSink tmSink;
    volatile long tmCount=0;
    final TimeService timeService;
    
    public FilePollingTmDataLink(String yamcsInstance, String name, String incomingDir) {
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+yamcsInstance+"]");
        this.incomingDir=incomingDir;
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    /**
     * used when no spec is specified, the incomingDir is based on the property with the same name from the yamcs.yaml
     * @param archiveInstance
     * @throws ConfigurationException
     */
    public FilePollingTmDataLink(String archiveInstance, String name) throws ConfigurationException {
        this(archiveInstance, name, YConfiguration.getConfiguration("yamcs").getString("incomingDir")
                +File.separator+archiveInstance+File.separator+"tm");
    }


    @Override
    public void run() {
        File fdir=new File(incomingDir);
        try {
            while(isRunning()) {
                if(!disabled && fdir.exists()) {
                    File[] files=fdir.listFiles();
                    Arrays.sort(files);
                    for(File f:files) {
                        log.info("Injecting the content of "+f);
                        try {
                            TmFileReader prov=new TmFileReader(f.getAbsolutePath());
                            PacketWithTime pwrt;
                            while((pwrt=prov.readPacket(timeService.getMissionTime()))!=null) {
                                tmSink.processPacket(pwrt);
                                tmCount++;
                            }
                        } catch (IOException e) {
                            log.warn("Got IOException while reading from "+f+": ", e);
                        }
                        if(!f.delete()) log.warn("Could not remove "+f);
                    }
                }
                Thread.sleep(10000);
            }
        } catch(InterruptedException e) {
            log.debug("Interrupted");
        }
    }

    @Override
    public String getDetailedStatus() {
        return "reading files from "+incomingDir;
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
    public boolean isArchiveReplay() {
        return false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getLinkStatus() {
        if (disabled) return "DISABLED";
        if(isRunning()) {
            return "OK";
        } else {
            return "UNAVAIL";
        }
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink=tmSink;
    }

    @Override
    public long getDataCount() {
        return tmCount;
    }
}
