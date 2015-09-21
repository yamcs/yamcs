package org.yamcs.simulator.launchland;

import java.io.IOException;

import org.yamcs.simulator.Simulator;
import org.yamcs.simulator.CCSDSPacket;

public class LaunchAndLandingSimulation extends Simulator {

    private CCSDSHandlerFlightData flightDataHandler = new CCSDSHandlerFlightData();
    private CCSDSHandlerDHS dhsHandler = new CCSDSHandlerDHS();
    private CCSDSHandlerPower powerDataHandler = new CCSDSHandlerPower();
    private CCSDSHandlerRCS rcsHandler = new CCSDSHandlerRCS();
    private CCSDSHandlerEPSLVPDU ESPLvpduHandler = new CCSDSHandlerEPSLVPDU();
    private CCSDSHandlerAck AckDataHandler = new CCSDSHandlerAck();
    
    private boolean engageHoldOneCycle = false;
    private boolean unengageHoldOneCycle = false;
    private int waitToEngage;
    private int waitToUnengage;
    private boolean engaged = false;
    private boolean unengaged = true;
    private boolean ExeTransmitted = true;
    private int battOneCommand;
    private int battTwoCommand;
    private int battThreeCommand;

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
                    if(waitToEngage == 2 || engaged) {
                        engaged = true;
                        //unengaged = false;
                        CCSDSPacket powerpacket = new CCSDSPacket(16 , 1);

                        powerDataHandler.fillPacket(powerpacket);

                        switch (battOneCommand) {
                            case 1: battOneCommand = 1;
                                powerDataHandler.setBattOneOff(powerpacket);
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 1, 0);
                                if (!ExeTransmitted ){
                                    transmitTM(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;

                            case 2: battOneCommand = 2;
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 1, 1);
                                if (!ExeTransmitted) {
                                    transmitTM(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                            default :
                                break;
                        }
                        switch (battTwoCommand) {
                            case 1:
                                battTwoCommand = 1;
                                powerDataHandler.setBattTwoOff(powerpacket);
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 2, 0);
                                if (!ExeTransmitted ){
                                    transmitTM(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;

                            case 2:
                                battTwoCommand = 2;
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 2, 1);
                                if (!ExeTransmitted){
                                    transmitTM(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                        }
                        switch (battThreeCommand) {
                            case 1:
                                battThreeCommand = 1;
                                powerDataHandler.setBattThreeOff(powerpacket);
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 3, 0);
                                if (!ExeTransmitted){
                                    transmitTM(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                            case 2:
                                battThreeCommand = 2;
                                AckDataHandler.fillExeCompPacket(exeCompPacket, 3, 1);
                                if (!ExeTransmitted){
                                    transmitTM(exeCompPacket);
                                    ExeTransmitted = true;
                                }
                                break;
                            default:
                                break;
                        }

                        transmitTM(powerpacket);

                        engageHoldOneCycle = false;
                        waitToEngage = 0;


                    } else if (waitToUnengage == 2 || unengaged){
                        CCSDSPacket powerpacket = new CCSDSPacket(16 , 1);
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
                    ESPLvpduHandler.fillPacket(packet);
                    transmitTM(packet);

                    if (engageHoldOneCycle) { // hold the command for 1 cycle after the command Ack received
                        waitToEngage = waitToEngage + 1;
                        System.out.println("Value : " + waitToEngage);
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

            CCSDSPacket ackPacket;
            if (commandPacket.getPacketType() == 10) {
                System.out.println("BATT COMMAND: " + commandPacket.getPacketId()+" batNum: "+commandPacket.getUserDataBuffer().get(0));

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
            case 1: batNum = 1; //switch bat1 on
                unengageHoldOneCycle = true;
                //engaged = false;
                ExeTransmitted = false;
                battOneCommand = 2;
                ackPacket = new CCSDSPacket(1, 2, 7);
                AckDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
            case 2:
                batNum = 2; //swtich bat2 on
                unengageHoldOneCycle = true;
                //engaged = false;
                ExeTransmitted = false;
                battTwoCommand = 2;
                ackPacket = new CCSDSPacket(1, 2, 7);
                AckDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
            case 3:
                batNum = 3;  //switch bat3 on
                unengageHoldOneCycle = true;
                //engaged = false;
                battThreeCommand = 2;
                ExeTransmitted = false;
                ackPacket = new CCSDSPacket(1, 2, 7);
                AckDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
        }
    }
    
    private void switchBatteryOff(CCSDSPacket commandPacket) {
        commandPacket.setPacketId(2);
        int batNum = commandPacket.getUserDataBuffer().get(0);
        CCSDSPacket ackPacket;
        switch(batNum) {
            case 1:
                batNum = 1; //switch bat1 off
                engageHoldOneCycle = true;
                ExeTransmitted = false;
                battOneCommand = 1;
                ackPacket = new CCSDSPacket(1, 2, 7);
                AckDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
                break;
            case 2: batNum = 2; //switch bat2 off
                engageHoldOneCycle = true;
                ExeTransmitted = false;
                battTwoCommand = 1;
                ackPacket = new CCSDSPacket(1, 2, 7);
                AckDataHandler.fillAckPacket(ackPacket, 1);
                transmitTM(ackPacket);
                break;
            case 3: batNum = 3; //switch bat3 off
                engageHoldOneCycle = true;
                ExeTransmitted = false;
                battThreeCommand = 1;
                ackPacket = new CCSDSPacket(1, 2, 7);
                AckDataHandler.fillAckPacket(ackPacket, 1);
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
        String fileName1 = new String(fileNameArray, 16, fileNameArray.length - 22);
        System.out.println("Command DUMP_RECORDING for file " + fileName1);
        dumpLosDataFile(fileName1);
    }
    
    private void deleteRecording(CCSDSPacket commandPacket) {
        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
        String fileName = new String(fileNameArray, 16, fileNameArray.length - 22);
        System.out.println("Command DELETE_RECORDING for file " + fileName);
        deleteLosDataFile(fileName);
    }
}
