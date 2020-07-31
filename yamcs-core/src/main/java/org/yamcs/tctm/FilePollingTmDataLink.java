package org.yamcs.tctm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;

/**
 * Reads telemetry files from the directory yamcs.incomingDir/tm
 *
 */
public class FilePollingTmDataLink extends AbstractTmDataLink implements Runnable {

    Path incomingDir;
    boolean deleteAfterImport = true;
    long delayBetweenPackets = -1;
    Thread thread;

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) {
        super.init(yamcsInstance, name, config);

        if (config.containsKey("incomingDir")) {
            incomingDir = Paths.get(config.getString("incomingDir"));
        } else {
            Path parent = YamcsServer.getServer().getIncomingDirectory();
            incomingDir = parent.resolve(yamcsInstance).resolve("tm");
        }
        deleteAfterImport = config.getBoolean("deleteAfterImport", true);
        delayBetweenPackets = config.getLong("delayBetweenPackets", -1);
    }

    @Override
    public void run() {
        File fdir = incomingDir.toFile();
        try {
            while (isRunningAndEnabled()) {
                if (fdir.exists()) {
                    play(fdir);
                }
                if (delayBetweenPackets < 0) {
                    Thread.sleep(10000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void play(File fdir) throws InterruptedException {
        File[] files = fdir.listFiles();
        Arrays.sort(files);
        for (File f : files) {
            if (f.isHidden() || !f.isFile()) {
                continue;
            }
            log.info("Injecting the content of {}", f);
            try {
                TmFileReader prov = getTmFileReader(f.getAbsolutePath());
                TmPacket pwrt;
                while ((pwrt = prov.readPacket(timeService.getMissionTime())) != null) {
                    tmSink.processPacket(pwrt);
                    updateStats(pwrt.getPacket().length);
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

    public TmFileReader getTmFileReader(String fileName) throws IOException {
        return new TmFileReader(fileName, packetPreprocessor);
    }

    @Override
    public String getDetailedStatus() {
        return "reading files from " + incomingDir;
    }

    @Override
    public void doDisable() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void doEnable() {
        thread = new Thread(this);
        thread.start();
    }

    @Override
    protected void doStart() {
        if (!isDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        doDisable();
        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
