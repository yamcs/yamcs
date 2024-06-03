package org.yamcs.tctm;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

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
 * <li>{@code deleteAfterImport} - if true (default), the files will be removed after being read.</li>
 * <li>{@code delayBetweenPackets} - if configured, it is the number of milliseconds to wait in between sending two
 * packets. By default it is -1 meaning the packets are sent as fast as possible.</li>
 * <li>{@code lastPacketStream} - If specified, emit the last packet to this stream. This is intended for batch imports,
 * where the content of the last packet should also be observable by realtime clients</li>
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
    Stream lastPacketStream;
    Thread thread;

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;

    @Override
    public Spec getSpec() {
        var spec = getDefaultSpec();
        spec.addOption("incomingDir", OptionType.STRING);
        spec.addOption("deleteAfterImport", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("delayBetweenPackets", OptionType.INTEGER);
        spec.addOption("lastPacketStream", OptionType.STRING);
        spec.addOption("headerSize", OptionType.INTEGER);
        spec.addOption("packetInputStreamClassName", OptionType.STRING);
        spec.addOption("packetInputStreamArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) {
        super.init(yamcsInstance, name, config);

        if (config.containsKey("incomingDir")) {
            incomingDir = Path.of(config.getString("incomingDir"));
        } else {
            log.warn("Deprecation warning: specify the incomingDir argument on the link " + name
                    + ". This will become required in a later version");
            Path parent = YamcsServer.getServer().getIncomingDirectory();
            incomingDir = parent.resolve(yamcsInstance).resolve("tm");
        }

        try {
            Files.createDirectories(incomingDir);
        } catch (IOException e) {
            log.warn("Failed to create directory: " + incomingDir);
        }

        deleteAfterImport = config.getBoolean("deleteAfterImport");
        delayBetweenPackets = config.getLong("delayBetweenPackets", -1);
        headerSize = config.getLong("headerSize", -1);
        packetInputStreamArgs = YConfiguration.emptyConfig();

        if (config.containsKey("lastPacketStream")) {
            var ydb = YarchDatabase.getInstance(yamcsInstance);
            var streamName = config.getString("lastPacketStream");
            lastPacketStream = ydb.getStream(streamName);
            if (lastPacketStream == null) {
                throw new ConfigurationException("Cannot find stream '" + streamName + "'");
            }
        }

        if (config.containsKey("packetInputStreamClassName")) {
            packetInputStreamClassName = config.getString("packetInputStreamClassName");
            packetInputStreamArgs = config.getConfigOrEmpty("packetInputStreamArgs");
        } else {
            packetInputStreamClassName = GenericPacketInputStream.class.getName();
            HashMap<String, Object> m = new HashMap<>();
            m.put("maxPacketLength", 1000);
            m.put("lengthFieldOffset", 4);
            m.put("lengthFieldLength", 2);
            m.put("lengthAdjustment", 7);
            m.put("initialBytesToStrip", 0);
            packetInputStreamArgs = YConfiguration.wrap(m);
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
            long minTime = TimeEncoding.POSITIVE_INFINITY;
            long maxTime = TimeEncoding.NEGATIVE_INFINITY;
            TmPacket tmPacket = null;
            try (PacketInputStream packetInputStream = getPacketInputStream(f.getAbsolutePath())) {
                byte[] packet;
                while ((packet = packetInputStream.readPacket()) != null) {
                    updateStats(packet.length);
                    tmPacket = new TmPacket(timeService.getMissionTime(), packet);
                    tmPacket.setEarthReceptionTime(erp);
                    tmPacket = packetPreprocessor.process(tmPacket);
                    if (tmPacket != null) {
                        minTime = Math.min(minTime, tmPacket.getGenerationTime());
                        maxTime = Math.max(maxTime, tmPacket.getGenerationTime());
                        count++;
                        processPacket(tmPacket);
                    }
                    if (delayBetweenPackets > 0) {
                        Thread.sleep(delayBetweenPackets);
                    }
                }
            } catch (EOFException e) {
                log.debug("{} finished", f);
            } catch (IOException | PacketTooLongException e) {
                log.warn("Exception while reading " + f, e);
            }

            if (tmPacket != null && lastPacketStream != null) {
                emitLastPacket(tmPacket);
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

    private void emitLastPacket(TmPacket tmPacket) {
        if (tmPacket.isInvalid()) {
            return;
        }

        Instant ertime = tmPacket.getEarthReceptionTime();
        Tuple t = null;
        if (ertime == Instant.INVALID_INSTANT) {
            ertime = null;
        }
        Long obt = tmPacket.getObt() == Long.MIN_VALUE ? null : tmPacket.getObt();
        String rootContainer = tmPacket.getRootContainer() != null
                ? tmPacket.getRootContainer().getQualifiedName()
                : null;
        t = new Tuple(StandardTupleDefinitions.TM, new Object[] {
                tmPacket.getGenerationTime(),
                tmPacket.getSeqCount(),
                tmPacket.getReceptionTime(),
                tmPacket.getStatus(),
                tmPacket.getPacket(),
                ertime,
                obt,
                getName(),
                rootContainer,
        });
        lastPacketStream.emitTuple(t);
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
