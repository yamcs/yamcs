package org.yamcs.simulator;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.yamcs.YConfiguration;
import org.yamcs.simulator.launchland.CCSDSHandlerAck;
import org.yamcs.simulator.launchland.CCSDSHandlerDHS;
import org.yamcs.simulator.launchland.CCSDSHandlerEPSLVPDU;
import org.yamcs.simulator.launchland.CCSDSHandlerFlightData;
import org.yamcs.simulator.launchland.CCSDSHandlerPower;
import org.yamcs.simulator.launchland.CCSDSHandlerRCS;
import org.yamcs.simulator.ui.SimWindow;


public class Simulator extends Thread {

    CCSDSHandlerFlightData 	flightDataHandler;
    CCSDSHandlerDHS 		dhsHandler;
    CCSDSHandlerPower 		powerDataHandler;
    CCSDSHandlerRCS 		rcsHandler;
    CCSDSHandlerEPSLVPDU 	ESPLvpduHandler;
    CCSDSHandlerAck         AckDataHandler;

    // configured via config file
    static boolean ui = false;
    static boolean losAos = false;
    static int losPeriodS = 60;
    static int aosPeriodS = 60;
    static List<ServerConnection> serversConnections;

    public boolean isLos = false;
    private TelemetryLink tl;
    LosStore losStore = new LosStore(this);

    private boolean engageHoldOneCycle = false;
    private boolean unengageHoldOneCycle = false;
    private int waitToEngage;
    private int waitToUnengage;
    private int DEFAULT_MAX_LENGTH=65542;
    private int maxLength = DEFAULT_MAX_LENGTH;
    private boolean engaged = false;
    private boolean unengaged = true;
    private boolean ExeTransmitted = true;
    private int battOneCommand;
    private int battTwoCommand;
    private int battThreeCommand;

    Queue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<>(100);//no more than 100 pending commands

    public static SimWindow simWindow = null;

    public int getNbServerNodes(){
        return serversConnections.size();
    }

    public Simulator() throws IOException {
        tl = new TelemetryLink(this, serversConnections);

        flightDataHandler  = new CCSDSHandlerFlightData();
        dhsHandler 		   = new CCSDSHandlerDHS();
        powerDataHandler   = new CCSDSHandlerPower();
        rcsHandler         = new CCSDSHandlerRCS();
        ESPLvpduHandler    = new CCSDSHandlerEPSLVPDU();
        AckDataHandler     = new CCSDSHandlerAck();

    }

    @Override
    public void run() {

        for(ServerConnection serverConnection : serversConnections ) {

            TelemetryLink.yamcsServerConnect(serverConnection);

            //start the TC reception thread;
            new Thread(() -> {
                while(true){
                    try {
                        // read commands
                        pendingCommands.addAll(readPackets(new DataInputStream(serverConnection.getTcSocket().getInputStream())));
                        Thread.sleep(4000);
                    } catch (IOException e) {
                        serverConnection.setConnected(false);
                        TelemetryLink.yamcsServerConnect(serverConnection);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            // start the TM transmission thread;
            (new Thread(() -> tl.packetSend(serverConnection))).start();
        }

        CCSDSPacket packet = null;

        try {
            for (int i = 0;;) {
                CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
                CCSDSPacket flightpacket = new CCSDSPacket(60, 33);
                flightDataHandler.fillPacket(flightpacket);
                tl.tmTransmit(flightpacket);


                if (i < 30) ++i;
                else {
                    if(waitToEngage == 2 || engaged  ){
                        engaged = true;
                        //unengaged = false;
                        CCSDSPacket powerpacket = new CCSDSPacket(16 , 1);

                        powerDataHandler.fillPacket(powerpacket);

                        switch(battOneCommand){
                            case 1: battOneCommand = 1;
                                powerDataHandler.setBattOneOff(powerpacket);
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 1, 0);
                                if (!ExeTransmitted ){
                                    tl.tmTransmit(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;

                            case 2: battOneCommand = 2;
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 1, 1);
                                if (!ExeTransmitted ){
                                    tl.tmTransmit(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                            default :
                                break;
                        }
                        switch(battTwoCommand){

                            case 1:battTwoCommand = 1;
                                powerDataHandler.setBattTwoOff(powerpacket);
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 2, 0);
                                if (!ExeTransmitted ){
                                    tl.tmTransmit(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;

                            case 2:battTwoCommand = 2;
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 2, 1);
                                if (!ExeTransmitted ){
                                    tl.tmTransmit(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                        }
                        switch(battThreeCommand){
                            case 1:battThreeCommand = 1;
                                powerDataHandler.setBattThreeOff(powerpacket);
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 3, 0);
                                if (!ExeTransmitted ){
                                    tl.tmTransmit(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                            case 2:battThreeCommand = 2;
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 3, 1);
                                if (!ExeTransmitted ){
                                    tl.tmTransmit(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                            default:
                                break;
                        }

                        tl.tmTransmit(powerpacket);

                        engageHoldOneCycle = false;
                        waitToEngage = 0;


                    } else if (waitToUnengage == 2 || unengaged ){
                        CCSDSPacket	powerpacket = new CCSDSPacket(16 , 1);
                        powerDataHandler.fillPacket(powerpacket);
                        tl.tmTransmit(powerpacket);
                        unengaged = true;
                        //engaged = false;

                        unengageHoldOneCycle = false;
                        waitToUnengage = 0;
                    }


                    packet = new CCSDSPacket(9, 2);
                    dhsHandler.fillPacket(packet);
                    tl.tmTransmit(packet);

                    packet = new CCSDSPacket(36, 3);
                    rcsHandler.fillPacket(packet);
                    tl.tmTransmit(packet);

                    packet = new CCSDSPacket(6, 4);
                    ESPLvpduHandler.fillPacket(packet);
                    tl.tmTransmit(packet);

                    if (engageHoldOneCycle){ // hold the command for 1 cycle after the command Ack received

                        waitToEngage = waitToEngage + 1;
                        System.out.println("Value : " + waitToEngage);

                    }

                    if (unengageHoldOneCycle){
                        waitToUnengage = waitToUnengage + 1;
                    }

                    i = 0;
                }

                executePendingCommands();
                Thread.sleep(4000 / 20);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Simulator thread ended");
    }

    /**
     * runs in the main TM thread, executes commands from the queue (if any)
     * @throws IOException
     */
    private void executePendingCommands() throws IOException {
        while(pendingCommands.size()>0) {
            CCSDSPacket commandPacket = pendingCommands.poll();

            CCSDSPacket ackPacket;
            if (commandPacket.packetType == 10) {
                System.out.println("BATT COMMAND  : " + commandPacket.packetid+" batNum: "+commandPacket.getUserDataBuffer().get(0));

                switch(commandPacket.packetid){

                    case 1 : commandPacket.packetid = 1 ; //switch on
                        int batNum = commandPacket.getUserDataBuffer().get(0);
                        switch(batNum) {
                            case 1: batNum = 1; //switch bat1 on
                                unengageHoldOneCycle = true;
                                //engaged = false;
                                ExeTransmitted = false;
                                battOneCommand = 2;
                                ackPacket = new CCSDSPacket(1, 2, 7);
                                AckDataHandler.fillAckPacket(ackPacket, 1);
                                tl.tmTransmit(ackPacket);
                            case 2: batNum = 2 ; //swtich bat2 on
                                unengageHoldOneCycle = true;
                                //engaged = false;
                                ExeTransmitted = false;
                                battTwoCommand = 2;
                                ackPacket = new CCSDSPacket(1, 2, 7);
                                AckDataHandler.fillAckPacket(ackPacket, 1);
                                tl.tmTransmit(ackPacket);
                            case 3:
                                batNum = 3;  //switch bat3 on
                                unengageHoldOneCycle = true;
                                //engaged = false;
                                battThreeCommand = 2;
                                ExeTransmitted = false;
                                ackPacket = new CCSDSPacket(1, 2, 7);
                                AckDataHandler.fillAckPacket(ackPacket, 1);
                                tl.tmTransmit(ackPacket);
                        }
                        break;
                    case 2: commandPacket.packetid = 2 ; //switch off
                        batNum = commandPacket.getUserDataBuffer().get(0);
                        switch(batNum) {

                            case 1: batNum = 1; //switch bat1 off
                                engageHoldOneCycle = true;
                                ExeTransmitted = false;
                                battOneCommand = 1;
                                ackPacket = new CCSDSPacket(1, 2, 7);
                                AckDataHandler.fillAckPacket(ackPacket, 1);
                                tl.tmTransmit(ackPacket);
                                break;
                            case 2: batNum = 2; //switch bat2 off
                                engageHoldOneCycle = true;
                                ExeTransmitted = false;
                                battTwoCommand = 1;
                                ackPacket = new CCSDSPacket(1, 2, 7);
                                AckDataHandler.fillAckPacket(ackPacket, 1);
                                tl.tmTransmit(ackPacket);
                                break;
                            case 3: batNum = 3; //switch bat3 off
                                engageHoldOneCycle = true;
                                ExeTransmitted = false;
                                battThreeCommand = 1;
                                ackPacket = new CCSDSPacket(1, 2, 7);
                                AckDataHandler.fillAckPacket(ackPacket, 1);
                                tl.tmTransmit(ackPacket);
                        }
                        break;
                    case 5 : // LIST_RECORDING
                        // send ack
                        //ackPacket = new CCSDSPacket(1, 2, 7);
                        //AckDataHandler.fillAckPacket(ackPacket, 1);
                        //tl.tmTransmit(ackPacket);

                        CCSDSPacket losNamePacket = losStore.getLosNames();
                        tl.tmTransmit(losNamePacket);
                        break;
                    case 6: {// DUMP_RECORDINGS
                        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
                        final String fileName = new String(fileNameArray, 16, fileNameArray.length - 22);
                        System.out.println("Command DUMP_RECORDING for file " + fileName);
                        dumpLosDataFile(fileName);
                        break;
                    }
                    case 7 : { //DELETE_RECORDING
                        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
                        final String fileName = new String(fileNameArray, 16, fileNameArray.length - 22);
                        System.out.println("Command DELETE_RECORDING for file " + fileName);
                        deleteLosDataFile(fileName);
                        break;
                    }
                }
            }
        }
    }




    /**
     * this runs in a separate thread but pushes commands to the main TM thread
     */
    private Queue<CCSDSPacket> readPackets(DataInputStream dIn) {
        Queue<CCSDSPacket> packetQueue = new ArrayBlockingQueue<>(1000);
        try {
            while(dIn.available() > 0) {
                //READ IN PACKET
                byte hdr[] = new byte[6];
                dIn.readFully(hdr);
                int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
                if(remaining>maxLength-6) throw new IOException("Remaining packet length too big: "+remaining+" maximum allowed is "+(maxLength-6));
                byte[] b = new byte[6+remaining];
                System.arraycopy(hdr, 0, b, 0, 6);
                dIn.readFully(b, 6, remaining);
                CCSDSPacket packet = new CCSDSPacket(ByteBuffer.wrap(b));
                packetQueue.add(packet);
            }
            //System.out.println("Packets Stored & Sent = "+ losStored + " : "+  losSent);
        }catch(IOException e) {
            System.err.println("Connection lost : " + e);
        }catch(Exception e) {
            System.err.println("Error reading command " + e);
            e.printStackTrace();
        }
        return packetQueue;
    }


    public void dumpLosDataFile(String filename)
    {
        // read data from los storage
        if(filename == null)
        {
            filename = losStore.getCurrentFileName();
        }
        DataInputStream dataStream = losStore.readLosFile(filename);
        if(dataStream == null)
            return;

        // extract packets from the data stream and put them in queue for downlink
        Queue<CCSDSPacket> qLosData = readPackets(dataStream);
        for(CCSDSPacket ccsdsPacket : qLosData)
        {
            for (ServerConnection serverConnection : serversConnections)
                            serverConnection.setTmDumpPacket(ccsdsPacket);

        }

        // add packet notifying that the file has been downloaded entirely
        CCSDSPacket confirmationPacket = CommandData.buildLosTransmittedRecordingPacket(filename);
        for(ServerConnection serverConnection : serversConnections)
            serverConnection.setTmDumpPacket(confirmationPacket);
    }

    public void deleteLosDataFile(String filename) {
        losStore.deleteFile(filename);
        // add packet notifying that the file has been deleted
        CCSDSPacket confirmationPacket = CommandData.buildLosDeletedRecordingPacket(filename);
        for(ServerConnection serverConnection : serversConnections)
            serverConnection.setTmDumpPacket(confirmationPacket);

    }

    public void startTriggeringLos() {
        losStore.startTriggeringLos();
    }

    public void stopTriggeringLos() {
        losStore.stopTriggeringLos();
    }


    private static void loadConfig() {
        System.out.println("Current directory: " + System.getProperty("user.dir"));
        YConfiguration.setup(System.getProperty("user.dir"));

        // read config from file
        YConfiguration yconfig = YConfiguration
                .getConfiguration("simulator");
        ui = yconfig.getBoolean("ui");
        losAos = yconfig.getBoolean("losAos");
        losPeriodS = yconfig.getInt("los_period_s");
        aosPeriodS = yconfig.getInt("aos_period_s");

        // load servers
        int i = 0;
        serversConnections = new LinkedList<>();
        Map<String, Object> servers=  yconfig.getMap("servers");
        for(String serverName : servers.keySet())
        {
            Map<String, Object> serverConfig = yconfig.getMap("servers", serverName);
            int tmPort = YConfiguration.getInt(serverConfig, "tmPort");
            int tcPort = YConfiguration.getInt(serverConfig, "tcPort");
            int dumPort = YConfiguration.getInt(serverConfig, "dumpPort");
            serversConnections .add(new ServerConnection(i++, tmPort, tcPort, dumPort));
        };

    }


    public static void main(String[] args) {
        System.out.println("_______________________\n");
        System.out.println(" ╦ ╦┌─┐┌─┐");
        System.out.println(" ╚╦╝├─┤└─┐");
        System.out.println("  ╩ ┴ ┴└─┘");
        System.out.println(" Yet Another Simulator");
        System.out.println("_______________________");

        loadConfig();
        try {
            Simulator client = new Simulator();

            // Start UI
            if(ui)
                simWindow = new SimWindow(client);

            // Start simulator itself
            client.start();

            // start alternating los and aos
            if(losAos && !ui)
            {
                client.startTriggeringLos();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
