package org.yamcs.tctm;

import java.util.List;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import static org.yamcs.xtce.NameDescription.qualifiedName;
import static org.yamcs.xtce.XtceDb.YAMCS_SPACESYSTEM_NAME;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.XtceDb;

/**
 * Sends raw packets on UDP socket.
 * 
 * @author nm
 *
 */
public class UdpTcDataLink extends AbstractThreadedTcDataLink {

    protected DatagramSocket socket;
    protected String host;
    protected int port;
    InetAddress address;
    private Parameter spPort;
    private Parameter spHost;
    private Parameter spAddress;
    private XtceDb mdb;

    @Override
    public void init(String yamcsInstance, String name, YConfiguration config) throws ConfigurationException {
        super.init(yamcsInstance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
        mdb = YamcsServer.getServer().getInstance(yamcsInstance).getXtceDb();
    }

    /**
     * adds system parameters link status and data in/out to the list.
     * <p>
     * The inheriting classes should call super.collectSystemParameters and then add their own parameters to the list
     * 
     * @param time
     * @param list
     */
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        super.collectSystemParameters(time, list);
        list.add(SystemParametersService.getPV(spPort, time, port));
        list.add(SystemParametersService.getPV(spHost, time, host));
        list.add(SystemParametersService.getPV(spAddress, time, address.getHostName()));

    }

    @Override
    public void setupSystemParameters(SystemParametersService sysParamCollector) {
        super.setupSystemParameters(sysParamCollector);

        IntegerParameterType intType = (IntegerParameterType) sysParamCollector.getBasicType(Type.UINT64);
        List<UnitType> unitSet = new ArrayList<>();
        unitSet.add(new UnitType(""));
        intType.setUnitSet(unitSet);
        StringParameterType stringType = (StringParameterType) sysParamCollector.getBasicType(Type.STRING);
        spPort = mdb.createSystemParameter(qualifiedName(YAMCS_SPACESYSTEM_NAME, linkName + "/port"), intType,
                "The port number this link is connected to");
        spHost = mdb.createSystemParameter(qualifiedName(YAMCS_SPACESYSTEM_NAME, linkName + "/ip"), stringType,
                "The ip address this link is connected to");
        spAddress = mdb.createSystemParameter(qualifiedName(YAMCS_SPACESYSTEM_NAME, linkName + "/address"), stringType,
                "The ip address this link is connected to");
    }

    @Override
    protected void startUp() throws SocketException, UnknownHostException {
        address = InetAddress.getByName(host);
        socket = new DatagramSocket();
    }

    @Override
    public String getDetailedStatus() {
        return String.format("OK, connected to %s:%d", host, port);
    }

    @Override
    public void shutDown() {
        socket.close();
    }

    @Override
    public void reconfigure(YConfiguration newConfig) throws UnknownHostException {
        port = newConfig.getInt("port");
        host = newConfig.getString("host");
        config.getRoot().put("host", port);
        config.getRoot().put("port", host);
        try {
            // Doing this here because if uplinkCommand throws an Exception, then yamcs stops sending commands
            address = InetAddress.getByName(host);
        } catch (Exception e) {
            log.warn("Address is not valid");
        }
    }

    @Override
    public void uplinkCommand(PreparedCommand pc) throws IOException {
        byte[] binary = cmdPostProcessor.process(pc);
        if (binary == null) {
            log.warn("command postprocessor did not process the command");
            return;
        }
        DatagramPacket packet = new DatagramPacket(binary, binary.length, address, port);
        socket.send(packet);
        dataCount.getAndIncrement();
        ackCommand(pc.getCommandId());
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }
}
