package org.yamcs.simulator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by msc on 29/05/15.
 */
public class LosStore {

    Simulator simulator;

    Thread tLosAos  = null;
    static SignalClock losClock;

    boolean triggerLos = false;

    // LOS related
    private OutputStream losOs = null;
    int losStored = 0;
    Path path = null;

    public LosStore(Simulator simulator)
    {
        this.simulator = simulator;
        losClock = new SignalClock(Simulator.losPeriodS, Simulator.aosPeriodS);
    }

    public void startTriggeringLos()
    {
        triggerLos = true;
        tLosAos = new Thread(() -> checkLosAos());
        tLosAos.start();
    }

    public void stopTriggeringLos(){
        triggerLos = false;
        try {
            tLosAos.interrupt();
            tLosAos.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void checkLosAos() {

        losClock.startClock();
        System.out.println("LOS/AOS started");

        while (triggerLos) {
            if(!simulator.isLos){
                try {
                    System.out.println("Waiting for los trigger");
                    losClock.getLosSignal().acquire();
                    createLosDataFile();
                    simulator.isLos = true;
                    System.out.println("Acquired LOS");
                } catch (InterruptedException e) {
                    System.out.println("Interrupted AOS period");
                } finally {
                    losClock.getLosSignal().release();
                }
            } else {
                try {
                    System.out.println("Waiting for end of los");
                    losClock.getAosSignal().acquire();
                    System.out.println("Aquired AOS");
                    simulator.isLos = false;
                    closeLosDataFile();
                } catch (InterruptedException e1) {
                    System.out.println("Interrupted LOS period");
                } finally {
                    losClock.getAosSignal().release();
                }
            }
        }
        // set simulator to non LOS
        System.out.println("Stopping the triggering of LOS/AOS period");
        simulator.isLos = false;
        closeLosDataFile();
    }

    private void closeLosDataFile(){
        try {
            System.out.println("close los file " );
            losOs.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createLosDataFile(){

        path = Paths.get(System.getProperty("user.dir") + "/losData/" + "tm_" + losClock.getTimeStamp() + ".dat");
        try {
            System.out.println("Creating file : " + path );

            Files.createDirectories(path.getParent());
            Files.createFile(path);

            losOs = new FileOutputStream(path.toFile(),false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public CCSDSPacket getLosNames(){

        CCSDSPacket losNamePacket = new CCSDSPacket(0, 2, 9);
        File folder = new File(System.getProperty("user.dir") + "/losData/");
        File[] listOfFiles = folder.listFiles();

        System.out.println("transmitLosNames " + listOfFiles.length);

        for (int i = 0; i < listOfFiles.length; i++) {

            losNamePacket.appendUserDataBuffer(listOfFiles[i].getName().toString().getBytes());
            //System.out.println("transmitLosNames, adding file " + listOfFiles[i].toString());
            if(i < listOfFiles.length-1)
                losNamePacket.appendUserDataBuffer(new String(" ").getBytes());
        }

        byte[] array = losNamePacket.getUserDataBuffer().array();
        int arrayLength = array.length;
        System.out.println("Recording names sent: " + new String(array, 16, arrayLength-16));

        // terminate string with 0
        losNamePacket.appendUserDataBuffer(new byte[1]);

        return losNamePacket;
    }


    public void tmPacketStore(CCSDSPacket packet) {
        try {
            packet.send(losOs);
        } catch (IOException e) {
            System.err.println("tmPacketStore : " + e);
        }
        losStored++;
        System.out.println("#" + losStored);
        System.out.println(packet.toString());
    }
}
