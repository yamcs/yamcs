package org.yamcs.tctm;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

/**
 * TM packet data link which reads telemetry files from a specified directory. The files are split into packets
 * according to the configure {@code packetInputStream}, run through the configured preprocessor and then sent on the
 * stream.
 * <p>
 * The data link scans continuously the incoming directory for new files. If multiple files are found, it processes them
 * in alphabetical order.
 * <p>
 * If the file is gzip-compressed, the {@link GZIPInputStream} is used to decompress it. To check if the file is
 * gzip-compressed, the first two bytes of the file are read and compared with 0x1F8B (gzip magic number).
 * <p>
 * Options:
 * <ul>
 * <li>{@code incomingDir} - the directory where the files are read from.</li>
 * <li>{@code delteAfterImport} - if true (default), the files will be removed after being read.</li>
 * <li>{@code delayBetweenPackets} - if configured, it is the number of milliseconds to wait in between sending two
 * packets. By default it is -1 meaning the packets are sent as fast as possible.</li>
 * <li>{@code headerSize} - if configured, the input files have a header which will be skipped before reading the first
 * packet.</li>
 * </ul>
 *
 */
public class FilePollingTmDataLink extends AbstractTmDataLink implements Runnable {

    Path incomingDir;
    boolean deleteAfterImport;
    long delayBetweenPackets = -1;
    long headerSize = -1l;
    Thread thread;

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("incomingDir", OptionType.STRING);
        spec.addOption("deleteAfterImport", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("delayBetweenPackets", OptionType.INTEGER);
        spec.addOption("headerSize", OptionType.INTEGER);
        spec.addOption("packetInputStreamClassName", OptionType.STRING);
        spec.addOption("packetInputStreamArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) {
        super.init(yamcsInstance, name, config);

        if (config.containsKey("incomingDir")) {
            incomingDir = Paths.get(config.getString("incomingDir"));
        } else {
            Path parent = YamcsServer.getServer().getIncomingDirectory();
            incomingDir = parent.resolve(yamcsInstance).resolve("tm");
        }
        deleteAfterImport = config.getBoolean("deleteAfterImport");
        delayBetweenPackets = config.getLong("delayBetweenPackets", -1);
        headerSize = config.getLong("headerSize", -1);
        packetInputStreamArgs = YConfiguration.emptyConfig();

        if (config.containsKey("packetInputStreamClassName")) {
            packetInputStreamClassName = config.getString("packetInputStreamClassName");
            packetInputStreamArgs = config.getConfigOrEmpty("packetInputStreamArgs");
        } else {// compatibility with the previous releases, should eventually be removed
            packetInputStreamClassName = UsocPacketInputStream.class.getName();
            packetPreprocessor = new IssPacketPreprocessor(yamcsInstance);
        }
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
        Instant erp = timeService.getHresMissionTime();
        File[] files = fdir.listFiles();
        Arrays.sort(files);
        for (File f : files) {
            if (!isRunningAndEnabled()) {
                return;
            }
            if (f.isHidden() || !f.isFile()) {
                continue;
            }
            log.info("Injecting the content of {}", f);
            long count = 0;
            long minTime = TimeEncoding.MAX_INSTANT;
            long maxTime = TimeEncoding.MIN_INSTANT;
            try (PacketInputStream packetInputStream = getPacketInputStream(f.getAbsolutePath())) {
                byte[] packet;
                while ((packet = packetInputStream.readPacket()) != null) {
                    updateStats(packet.length);
                    TmPacket tmPacket = new TmPacket(timeService.getMissionTime(), packet);
                    tmPacket.setEarthReceptionTime(erp);
                    TmPacket tmpkt = packetPreprocessor.process(tmPacket);
                    minTime = Math.min(minTime, tmpkt.getGenerationTime());
                    maxTime = Math.max(maxTime, tmpkt.getGenerationTime());
                    count++;
                    processPacket(tmpkt);
                    if (delayBetweenPackets > 0) {
                        Thread.sleep(delayBetweenPackets);
                    }
                }
            } catch (EOFException e) {
                log.debug("{} finished", f);
            } catch (IOException | PacketTooLongException e) {
                log.warn("Got IOException while reading from " + f + ": ", e);
            }
            String msg = String.format("Ingested %s; pkt count: %d, time range: [%s, %s]", f, count,
                    TimeEncoding.toString(minTime), TimeEncoding.toString(maxTime));
            eventProducer.sendInfo("FILE_INGESTION", msg);
            if (deleteAfterImport) {
                if (!f.delete()) {
                    log.warn("Could not remove {}", f);
                }
            }
        }
    }

    private PacketInputStream getPacketInputStream(String fileName) throws IOException {
        boolean gzip = false;
        try (InputStream inputStream = new FileInputStream(fileName)) {
            // read the first two bytes to check if it's gzip
            byte[] b = new byte[2];
            int x = inputStream.read(b);
            if ((x == 2) && (b[0] == 0x1F) && ((b[1] & 0xFF) == 0x8B)) {
                gzip = true;
            }
        }

        InputStream inputStream = gzip ? new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName)))
                : new BufferedInputStream(new FileInputStream(fileName));
        if (headerSize > 0) {
            long n = inputStream.skip(headerSize);
            if (n != headerSize) {
                inputStream.close();
                throw new IOException(
                        "Short read: only" + n + " out of " + headerSize + "header bytes could be skipped");
            }
        }
        PacketInputStream packetInputStream;
        try {
            packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName);
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            inputStream.close();
            throw e;
        }
        packetInputStream.init(inputStream, packetInputStreamArgs);
        return packetInputStream;
    }

    public static InputStream getInputStream(String fileName) throws IOException {
        boolean gzip = false;
        try (InputStream inputStream = new FileInputStream(fileName)) {
            // read the first two bytes to check if it's gzip
            byte[] b = new byte[2];
            int x = inputStream.read(b);
            if ((x == 2) && (b[0] == 0x1F) && ((b[1] & 0xFF) == 0x8B)) {
                gzip = true;
            }
        }

        InputStream inputStream;
        if (gzip) {
            inputStream = new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName)));
        } else {
            inputStream = new BufferedInputStream(new FileInputStream(fileName));
        }
        return inputStream;
    }

    @Override
    public String getDetailedStatus() {
        return "Reading files from " + incomingDir;
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
        thread.setName(getClass().getSimpleName() + "-" + linkName);
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
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                notifyFailed(e);
                return;
            }
        }
        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
