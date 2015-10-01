package org.yamcs.simulator.launchland;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.SimulationConfiguration;
import org.yamcs.simulator.Simulator;

public class LaunchAndLandingSimulator extends Simulator {
    
    private static final Logger log = LoggerFactory.getLogger(LaunchAndLandingSimulator.class);

    private FlightDataHandler flightDataHandler = new FlightDataHandler();
    private DHSHandler dhsHandler = new DHSHandler();
    private PowerHandler powerDataHandler = new PowerHandler();
    private RCSHandler rcsHandler = new RCSHandler();
    private EpsLvpduHandler epslvpduHandler = new EpsLvpduHandler();
    private AckHandler ackDataHandler = new AckHandler();
    
    private boolean engageHoldOneCycle = false;
    private boolean unengageHoldOneCycle = false;
    private int waitToEngage;
    private int waitToUnengage;
    private boolean engaged = false;
    private boolean unengaged = true;
    private boolean exeTransmitted = true;
    private int battOneCommand;
    private int battTwoCommand;
    private int battThreeCommand;
    
    public LaunchAndLandingSimulator(SimulationConfiguration simConfig) {
        super(simConfig);
    }

    @Override
    public void run() {
        super.run();
        CCSDSPacket packet = null;
        try {
            for (int i = 0;;) {
                CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
                CCSDSPacket flightpacket = new CCSDSPacket(60, 33);
                flightDataHandler.fillPacket(flightpacket);
                transmitTM(flightpacket);

                if (i < 30) ++i;
                else {
                    if (waitToEngage == 2 || engaged) {
                        engaged = true;
                        //unengaged = false;
                        CCSDSPacket powerpacket = new CCSDSPacket(16, 1);

                        powerDataHandler.fillPacket(powerpacket);

                        switch (battOneCommand) {
                            case 1: 
                                powerDataHandler.setBattOneOff(powerpacket);
                                ackDataHandler.fillExeCompPacket(exeCompPacket, 1, 0);
                                if (!exeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    exeTransmitted = true;
                                }
                                break;

                            case 2:
                                ackDataHandler.fillExeCompPacket(exeCompPacket, 1, 1);
                                if (!exeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    exeTransmitted = true;
                                }
                                break;
                        }
                        switch (battTwoCommand) {
                            case 1:
                                powerDataHandler.setBattTwoOff(powerpacket);
                                ackDataHandler.fillExeCompPacket(exeCompPacket, 2, 0);
                                if (!exeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    exeTransmitted = true;
                                }
                                break;

                            case 2:
                                ackDataHandler.fillExeCompPacket(exeCompPacket, 2, 1);
                                if (!exeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    exeTransmitted = true;
                                }
                                break;
                        }
                        switch (battThreeCommand) {
                            case 1:
                                powerDataHandler.setBattThreeOff(powerpacket);
                                ackDataHandler.fillExeCompPacket(exeCompPacket, 3, 0);
                                if (!exeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    exeTransmitted = true;
                                }
                                break;
                                
                            case 2:
                                ackDataHandler.fillExeCompPacket(exeCompPacket, 3, 1);
                                if (!exeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    exeTransmitted = true;
                                }
                                break;
                        }

                        transmitTM(powerpacket);

                        engageHoldOneCycle = false;
                        waitToEngage = 0;

                    } else if (waitToUnengage == 2 || unengaged) {
                        CCSDSPacket powerpacket = new CCSDSPacket(16, 1);
                        powerDataHandler.fillPacket(powerpacket);
                        transmitTM(powerpacket);
                        unengaged = true;
                        //engaged = false;

                        unengageHoldOneCycle = false;
                        waitToUnengage = 0;
                    }

                    packet = new CCSDSPacket(9, 2);
                    dhsHandler.fillPacket(packet);
                    transmitTM(packet);

                    packet = new CCSDSPacket(36, 3);
                    rcsHandler.fillPacket(packet);
                    transmitTM(packet);

                    packet = new CCSDSPacket(6, 4);
                    epslvpduHandler.fillPacket(packet);
                    transmitTM(packet);

                    if (engageHoldOneCycle) { // hold the command for 1 cycle after the command Ack received
                        waitToEngage = waitToEngage + 1;
                        log.debug("Value : " + waitToEngage);
                    }

                    if (unengageHoldOneCycle) {
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
    }
    
    /**
     * runs in the main TM thread, executes commands from the queue (if any)
     */
    private void executePendingCommands() throws IOException {
        while(pendingCommands.size()>0) {
            CCSDSPacket commandPacket = pendingCommands.poll();
            if (commandPacket.getPacketType() == 10) {
                log.debug("BATT COMMAND: " + commandPacket.getPacketId()+" batNum: "+commandPacket.getUserDataBuffer().get(0));

                switch(commandPacket.getPacketId()){
                    case 1:
                        switchBatteryOn(commandPacket);
                        break;
                    case 2:
                        switchBatteryOff(commandPacket);
                        break;
                    case 5:
                        listRecordings();
                        break;
                    case 6:
                        dumpRecording(commandPacket);
                        break;
                    case 7:
                        deleteRecording(commandPacket);
                        break;
                }
            }
        }
    }
    
    private void switchBatteryOn(CCSDSPacket commandPacket) {
        commandPacket.setPacketId(1);
        int batNum = commandPacket.getUserDataBuffer().get(0);
        CCSDSPacket ackPacket;
        switch(batNum) {
            case 1:
                unengageHoldOneCycle = true;
                //engaged = false;
                exeTransmitted = false;
                battOneCommand = 2;
                ackPacket = new CCSDSPacket(1, 2, 7);
                ackDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
                break;
            case 2:
                unengageHoldOneCycle = true;
                //engaged = false;
                exeTransmitted = false;
                battTwoCommand = 2;
                ackPacket = new CCSDSPacket(1, 2, 7);
                ackDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
                break;
            case 3:
                unengageHoldOneCycle = true;
                //engaged = false;
                battThreeCommand = 2;
                exeTransmitted = false;
                ackPacket = new CCSDSPacket(1, 2, 7);
                ackDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
        }
    }
    
    private void switchBatteryOff(CCSDSPacket commandPacket) {
        commandPacket.setPacketId(2);
        int batNum = commandPacket.getUserDataBuffer().get(0);
        CCSDSPacket ackPacket;
        switch(batNum) {
            case 1:
                engageHoldOneCycle = true;
                exeTransmitted = false;
                battOneCommand = 1;
                ackPacket = new CCSDSPacket(1, 2, 7);
                ackDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
                break;
            case 2:
                engageHoldOneCycle = true;
                exeTransmitted = false;
                battTwoCommand = 1;
                ackPacket = new CCSDSPacket(1, 2, 7);
                ackDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
                break;
            case 3:
                engageHoldOneCycle = true;
                exeTransmitted = false;
                battThreeCommand = 1;
                ackPacket = new CCSDSPacket(1, 2, 7);
                ackDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
        }
    }
    
    private void listRecordings() {
        // send ack
        //ackPacket = new CCSDSPacket(1, 2, 7);
        //AckDataHandler.fillAckPacket(ackPacket, 1);
        //tl.tmTransmit(ackPacket);

        CCSDSPacket losNamePacket = getLosStore().getLosNames();
        transmitTM(losNamePacket);
    }
    
    private void dumpRecording(CCSDSPacket commandPacket) {
        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
        int indexStartOfString = 16;
        int indexEndOfString = indexStartOfString;
        for(int i = indexStartOfString; i< fileNameArray.length; i++)
        {
            if(fileNameArray[i] == 0) {
                indexEndOfString = i;
                break;
            }
        }
        String fileName1 = new String(fileNameArray, indexStartOfString, indexEndOfString - indexStartOfString);
        log.info("Command DUMP_RECORDING for file " + fileName1);
        dumpLosDataFile(fileName1);
    }
    
    private void deleteRecording(CCSDSPacket commandPacket) {
        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
        String fileName = new String(fileNameArray, 16, fileNameArray.length - 22);
        log.info("Command DELETE_RECORDING for file " + fileName);
        deleteLosDataFile(fileName);
    }
}
