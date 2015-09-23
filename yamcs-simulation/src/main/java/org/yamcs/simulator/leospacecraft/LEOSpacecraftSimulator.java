package org.yamcs.simulator.leospacecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.SimulationConfiguration;
import org.yamcs.simulator.SimulationData;
import org.yamcs.simulator.Simulator;
import org.yamcs.simulator.launchland.AckHandler;

/**
 * Discrete 1Hz time-stepped simulation of a generic LEO spacecraft.
 */
public class LEOSpacecraftSimulator extends Simulator {
    
    private static final Logger log = LoggerFactory.getLogger(LEOSpacecraftSimulator.class);
    private static final int OFF = 0;
    private static final int ON = 1;
    
    private DataFeeder packetFeeder = new DataFeeder(true);
    private LEOSpacecraftModel model = new LEOSpacecraftModel();
    private long t = 0;
    
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
    
    public LEOSpacecraftSimulator(SimulationConfiguration simConfig) {
        super(simConfig);
    }
    
    @Override
    public void run() {
        super.run();
        try {
            SimulationData data;
            while ((data = packetFeeder.readNext()) != null) {
                model.step(t++, data);
                
                // Hmm :-/
                if (waitToEngage == 2 || engaged) {
                    applyModifications(model); // 2
                    
                    CCSDSPacket packet = model.toCCSDSPacket();
                    transmitTM(packet);
                    
                    engageHoldOneCycle = false;
                    waitToEngage = 0;
                } else if (waitToUnengage == 2 || unengaged) {
                    CCSDSPacket packet = model.toCCSDSPacket();
                    transmitTM(packet);
                    
                    unengaged = true;
                    unengageHoldOneCycle = false;
                    waitToUnengage = 0;
                }
                
                // hold the command for 1 cycle after the command Ack received
                if (engageHoldOneCycle) {
                    waitToEngage += 1;
                }

                if (unengageHoldOneCycle) {
                    waitToUnengage += 1;
                }
                
                executePendingCommands(); // 1
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Hmm, this feels like it could be done better
     */
    private void applyModifications(LEOSpacecraftModel model) {
        engaged = true;
        //unengaged = false;

        CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
        switch (battOneCommand) {
        case 1:
            model.forceBatteryOneOff(true);
            ackDataHandler.fillExeCompPacket(exeCompPacket, 1, OFF);
            if (!exeTransmitted) {
                transmitTM(exeCompPacket);
                exeTransmitted = true;
            }
            break;

        case 2:
            model.forceBatteryOneOff(false);
            ackDataHandler.fillExeCompPacket(exeCompPacket, 1, ON);
            if (!exeTransmitted) {
                transmitTM(exeCompPacket);
                exeTransmitted = true;
            }
            break;
        }
        
        switch (battTwoCommand) {
        case 1:
            model.forceBatteryTwoOff(true);
            ackDataHandler.fillExeCompPacket(exeCompPacket, 2, OFF);
            if (!exeTransmitted) {
                transmitTM(exeCompPacket);
                exeTransmitted = true;
            }
            break;

        case 2:
            model.forceBatteryTwoOff(false);
            ackDataHandler.fillExeCompPacket(exeCompPacket, 2, ON);
            if (!exeTransmitted) {
                transmitTM(exeCompPacket);
                exeTransmitted = true;
            }
            break;
        }
    }
    
    private void executePendingCommands() {
        while(pendingCommands.size()>0) {
            CCSDSPacket commandPacket = pendingCommands.poll();
            if (commandPacket.getPacketType() == 10) {
                log.debug("BATT COMMAND: " + commandPacket.getPacketId()+" batNum: "+commandPacket.getUserDataBuffer().get(0));

                switch(commandPacket.getPacketId()) {
                case 1:
                    switchBatteryOn(commandPacket);
                    break;
                case 2:
                    switchBatteryOff(commandPacket);
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
        }
    }
}
