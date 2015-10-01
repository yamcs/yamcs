package org.yamcs.simulator;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by msc on 29/05/15.
 */
public class LosStore {

    Simulator simulation;

    Thread tLosAos  = null;
    static SignalClock losClock;

    boolean triggerLos = false;

    // LOS related
    private OutputStream losOs = null;
    int losStored = 0;
    Path path = null;

    public LosStore(Simulator simulator, SimulationConfiguration simConfig)
    {
        this.simulation = simulator;
        losClock = new SignalClock(simConfig.getLOSPeriod(), simConfig.getAOSPeriod());
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
            if(!simulation.isLOS()) {
                try {
                    System.out.println("Waiting for los trigger");
                    losClock.getLosSignal().acquire();
                    createLosDataFile();
                    simulation.setLOS(true);
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
                    simulation.setLOS(false);
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
        simulation.setLOS(false);
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
        try {
            File folder = new File(System.getProperty("user.dir") + "/losData/");
            File[] listOfFiles = folder.listFiles();

            System.out.println("transmitLosNames " + listOfFiles.length);

            for (int i = 0; i < listOfFiles.length; i++) {

                losNamePacket.appendUserDataBuffer(listOfFiles[i].getName().toString().getBytes());
                //System.out.println("transmitLosNames, adding file " + listOfFiles[i].toString());
                if (i < listOfFiles.length - 1)
                    losNamePacket.appendUserDataBuffer(new String(" ").getBytes());
            }

            byte[] array = losNamePacket.getUserDataBuffer().array();
            int arrayLength = array.length;
            System.out.println("Recording names sent: " + new String(array, 16, arrayLength - 16));

            // terminate string with 0
            losNamePacket.appendUserDataBuffer(new byte[1]);
        }
        catch (Exception e)
        {
            System.out.println("Unable to get los recordings: " + e.getMessage());
        }
        return losNamePacket;
    }

    public DataInputStream readLosFile(String fileName)
    {
        Path requestedFile = null;
        DataInputStream datas = null;

        if(fileName == null)
        {
            requestedFile = path;
        }
        else
        {
            requestedFile = Paths.get(System.getProperty("user.dir") + "/losData/" + fileName);
        }
        if(requestedFile == null)
        {
            System.out.println("No LOS data file to dump.");
            return null;
        }
        System.out.println("readLosFile :" + requestedFile.toString());

        try {

            FileInputStream fStream = new FileInputStream(requestedFile.toFile());
            datas = new DataInputStream(fStream);

            // disable additional downloads of the current file
            path = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return datas;
    }


    public void tmPacketStore(CCSDSPacket packet) {
        try {
            packet.writeTo(losOs);
        } catch (IOException e) {
            System.err.println("tmPacketStore : " + e);
        }
        losStored++;
        System.out.println("#" + losStored);
        System.out.println(packet.toString());
    }

    public void deleteFile(String filename) {
        Path fileToDelete = Paths.get(System.getProperty("user.dir") + "/losData/" + filename);
        System.out.println("losDeleteFile :" + fileToDelete.toString());
        try{
            fileToDelete.toFile().delete();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public String getCurrentFileName() {
        if(path == null)
             return null;
        return  path.toFile().getName();
    }
}
