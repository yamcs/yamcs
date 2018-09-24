package org.yamcs.tctm;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.YObjectLoader;

public class TcpTmDataLink extends AbstractTmDataLink {
    protected volatile long packetcount = 0;
    protected Socket tmSocket;
    protected String host = "localhost";
    protected int port = 10031;
    protected volatile boolean disabled = false;

    protected final Logger log;
    private TmSink tmSink;

    private SystemParametersCollector sysParamCollector;
    ParameterValue svConnectionStatus;
    List<ParameterValue> sysVariables = new ArrayList<>();
    private String spLinkStatus, spDataCount;
    final String yamcsInstance;
    final String name;
    final protected TimeService timeService;
    String packetInputStreamClassName;
    Object packetInputStreamArgs;
    PacketInputStream packetInputStream;

    protected TcpTmDataLink(String instance, String name) {// dummy constructor needed by subclass constructors
        this.yamcsInstance = instance;
        this.name = name;
        this.timeService = YamcsServer.getTimeService(instance);
        this.packetInputStreamClassName = CcsdsPacketInputStream.class.getName();
        this.packetInputStreamArgs = null;
        log = LoggingUtils.getLogger(this.getClass(), instance);
        initPreprocessor(instance, null);
    }

    public TcpTmDataLink(String instance, String name, String spec) throws ConfigurationException {
        this(instance, name, YConfiguration.getConfiguration("tcp").getMap(spec));
    }

    public TcpTmDataLink(String instance, String name, Map<String, Object> args) throws ConfigurationException {
        this(instance, name);
        if (args.containsKey("tmHost")) { // this is when the config is specified in tcp.yaml
            host = YConfiguration.getString(args, "tmHost");
            port = YConfiguration.getInt(args, "tmPort");
        } else {
            host = YConfiguration.getString(args, "host");
            port = YConfiguration.getInt(args, "port");
        }
        this.packetInputStreamClassName = YConfiguration.getString(args, "packetInputStreamClassName",
                CcsdsPacketInputStream.class.getName());
        this.packetInputStreamArgs = args.get("packetInputStreamArgs");

        initPreprocessor(instance, args);
    }

    protected void openSocket() throws IOException {
        InetAddress address = InetAddress.getByName(host);
        tmSocket = new Socket();
        tmSocket.setKeepAlive(true);
        tmSocket.connect(new InetSocketAddress(address, port), 1000);
        try {
            if (packetInputStreamArgs != null) {
                packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName, tmSocket.getInputStream(),
                        packetInputStreamArgs);
            } else {
                packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName, tmSocket.getInputStream());
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw e;
        }
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    public void run() {
        setupSysVariables();
        while (isRunning()) {
            PacketWithTime pwrt = getNextPacket();
            if (pwrt == null) {
                break;
            }
            tmSink.processPacket(pwrt);
        }
    }

    public PacketWithTime getNextPacket() {
        PacketWithTime pwt = null;
        while (isRunning()) {
            while (disabled) {
                if (!isRunning()) {
                    return null;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                if (tmSocket == null) {
                    openSocket();
                    log.info("TM connection established to {}:{}", host, port);
                }
                byte[] packet = packetInputStream.readPacket();
                packetcount++;
                pwt = packetPreprocessor.process(packet);
                if (pwt != null) {
                    break;
                }
            } catch (EOFException e) {
                log.warn("Tm Connection closed");
                tmSocket = null;
            } catch (IOException e) {
                String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
                log.info("Cannot open or read TM socket {}:{} {}'. Retrying in 10s", host, port, exc);
                try {
                    tmSocket.close();
                } catch (Exception e2) {
                }
                tmSocket = null;
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return pwt;
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (tmSocket == null) {
            return Status.UNAVAIL;
        } else {
            return Status.OK;
        }
    }

    @Override
    public void triggerShutdown() {
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket = null;
        }
        if (sysParamCollector != null) {
            sysParamCollector.unregisterProducer(this);
        }
    }

    @Override
    public void disable() {
        disabled = true;
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket = null;
        }
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
        if (disabled) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (tmSocket == null) {
            return String.format("Not connected to %s:%d", host, port);
        } else {
            return String.format("OK, connected to %s:%d, received %d packets", host, port, packetcount);
        }
    }

    @Override
    public long getDataCount() {
        return packetcount;
    }

    protected void setupSysVariables() {
        this.sysParamCollector = SystemParametersCollector.getInstance(yamcsInstance);
        if (sysParamCollector != null) {
            sysParamCollector.registerProducer(this);
            spLinkStatus = sysParamCollector.getNamespace() + "/" + name + "/linkStatus";
            spDataCount = sysParamCollector.getNamespace() + "/" + name + "/dataCount";

        } else {
            log.info("System variables collector not defined for instance {} ", yamcsInstance);
        }
    }

    @Override
    public Collection<ParameterValue> getSystemParameters() {
        long time = timeService.getMissionTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(spLinkStatus, time, getLinkStatus().name());
        ParameterValue dataCount = SystemParametersCollector.getPV(spDataCount, time, getDataCount());
        return Arrays.asList(linkStatus, dataCount);
    }
}
