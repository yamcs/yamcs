package org.yamcs.tctm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;

/**
 * Reads telemetry files from the directory yamcs.incomingDir/tm
 *
 */
public class FilePollingTmDataLink extends AbstractTmDataLink {
    final String incomingDir;
    final private Logger log;
    volatile boolean disabled;
    TmSink tmSink;
    volatile long tmCount = 0;
    final TimeService timeService;
    boolean deleteAfterImport = true;
    long delayBetweenPackets = -1;

    public FilePollingTmDataLink(String yamcsInstance, String name, Map<String, Object> args) {
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);

        this.incomingDir = YConfiguration.getString(args, "incomingDir", getDefaultIncomingDir(yamcsInstance));
        this.deleteAfterImport = YConfiguration.getBoolean(args, "deleteAfterImport", true);
        this.delayBetweenPackets = YConfiguration.getLong(args, "delayBetweenPackets", -1);
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        initPreprocessor(yamcsInstance, args);
    }

    public FilePollingTmDataLink(String yamcsInstance, String name, String incomingDir) {
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);
        this.incomingDir = incomingDir;
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        initPreprocessor(yamcsInstance, null);
    }

    /**
     * used when no spec is specified, the incomingDir is based on the property with the same name from the yamcs.yaml
     * 
     * @param instance
     * @throws ConfigurationException
     */
    public FilePollingTmDataLink(String instance, String name) throws ConfigurationException {
        this(instance, name, getDefaultIncomingDir(instance));
    }

    static String getDefaultIncomingDir(String instance) {
        return YConfiguration.getConfiguration("yamcs").getString("incomingDir")
                + File.separator + instance + File.separator + "tm";
    }

    @Override
    public void run() {
        File fdir = new File(incomingDir);
        try {
            while (isRunning()) {
                if (!disabled && fdir.exists()) {
                    File[] files = fdir.listFiles();
                    Arrays.sort(files);
                    for (File f : files) {
                        log.info("Injecting the content of {}", f);
                        try {
                            TmFileReader prov = getTmFileReader(f.getAbsolutePath());
                            PacketWithTime pwrt;
                            while ((pwrt = prov.readPacket(timeService.getMissionTime())) != null) {
                                tmSink.processPacket(pwrt);
                                tmCount++;
                                if (delayBetweenPackets > 0) {
                                    Thread.sleep(delayBetweenPackets);
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Got IOException while reading from " + f + ": ", e);
                        }
                        if (deleteAfterImport) {
                            if (!f.delete()) {
                                log.warn("Could not remove {}", f);
                            }
                        }
                    }
                }
                if (delayBetweenPackets < 0) {
                    Thread.sleep(10000);
                }
            }
        } catch (InterruptedException e) {
            log.debug("Interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public TmFileReader getTmFileReader(String fileName) throws IOException {
        return new TmFileReader(fileName, packetPreprocessor);
    }

    @Override
    public String getDetailedStatus() {
        return "reading files from " + incomingDir;
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
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (isRunning()) {
            return Status.OK;
        } else {
            return Status.UNAVAIL;
        }
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    public long getDataCount() {
        return tmCount;
    }
}
