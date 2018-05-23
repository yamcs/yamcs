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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by msc on 29/05/15.
 */
public class LosStore {

    private static SignalClock losClock;
	
	private Simulator simulation;
    private Thread tLosAos  = null;
    private OutputStream losOs = null;
    private Path path = null;

    private boolean triggerLos = false;
    private int losStored = 0;
    
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());


    public LosStore(Simulator simulator, SimulationConfiguration simConfig) {
        this.simulation = simulator;
        losClock = new SignalClock(simConfig.getLOSPeriod(), simConfig.getAOSPeriod());
    }

    public void startTriggeringLos() {
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
    
    public CCSDSPacket getLosNames(){

        CCSDSPacket losNamePacket = new CCSDSPacket(0, 2, 9, false);
        try {
            File folder = new File(System.getProperty("user.dir") + "/losData/");
            File[] listOfFiles = folder.listFiles() == null? new File[0] : folder.listFiles(); //To avoid a null pointer in case of the folder not existing
            log.debug("Los files list size: {}", listOfFiles.length);

            for (int i = 0; i < listOfFiles.length; i++) {

                losNamePacket.appendUserDataBuffer(listOfFiles[i].getName().toString().getBytes());              
                if (i < listOfFiles.length - 1)
                    losNamePacket.appendUserDataBuffer(new String(" ").getBytes());
            }

            byte[] array = losNamePacket.getUserDataBuffer().array();
            int arrayLength = array.length;
            log.debug("Recording names sent: {}", new String(array, 16, arrayLength - 16));

            // terminate string with 0
            losNamePacket.appendUserDataBuffer(new byte[1]);
        }
        catch (Exception e) {
            log.warn("Unable to get los recordings: " + e.getMessage(), e);
        }
        return losNamePacket;
    }

    public DataInputStream readLosFile(String fileName) {
    	
        Path requestedFile = null;
        DataInputStream datas = null;

        if(fileName == null){
            requestedFile = path;
        } else {
            requestedFile = Paths.get(System.getProperty("user.dir") + "/losData/" + fileName);
        }
        
        if(requestedFile == null){
        	log.debug("No LOS data file to dump.");
            return null;
        }     
        log.debug("readLosFile: {}",  requestedFile);

        try {
        	
        	FileInputStream fStream = new FileInputStream(requestedFile.toFile());
            datas = new DataInputStream(fStream);
            // disable additional downloads of the current file
            path = null;
        } catch (IOException e) {
        	log.error("readLosFile: " + e.getMessage(), e);
        }
        return datas;
    }


    public void tmPacketStore(CCSDSPacket packet) {
        try {
            packet.writeTo(losOs);
        } catch (IOException e) {
            log.error("tmPacketStore: " + e.getMessage(), e);
        }
        losStored++;
       
        log.debug("#{}", losStored);
        log.debug(packet.toString());

    }

    public void deleteFile(String filename) {
        Path fileToDelete = Paths.get(System.getProperty("user.dir") + "/losData/" + filename);
        log.debug("Delete Los File: {}", fileToDelete);
        try{
            fileToDelete.toFile().delete();
        } catch(Exception e){
        	log.error("Error deleting Los file: " + e.getMessage(), e);
 
        }
    }

    public String getCurrentFileName() {
        if(path == null)
             return null;
        return  path.toFile().getName();
    }


    private void checkLosAos() {

        losClock.startClock();
        log.info("LOS/AOS started");

        while (triggerLos) {
            if(!simulation.isLOS()) {
                try {
                    log.debug("Waiting for los trigger");
                    losClock.getLosSignal().acquire();
                    createLosDataFile();
                    simulation.setLOS(true);
                    log.debug("Acquired LOS");
                } catch (InterruptedException e) {
                    log.warn("Interrupted AOS period");
                } finally {
                    losClock.getLosSignal().release();
                }
            } else {
                try {
                    log.debug("Waiting for end of los");
                    losClock.getAosSignal().acquire();
                    log.debug("Aquired AOS");
                    simulation.setLOS(false);
                    closeLosDataFile();
                } catch (InterruptedException e1) {
                    log.warn("Interrupted LOS period");
                } finally {
                    losClock.getAosSignal().release();
                }
            }
        }
        // set simulator to non LOS
        log.info("Stopping the triggering of LOS/AOS period");
        simulation.setLOS(false);
        closeLosDataFile();
    }

    private void closeLosDataFile(){
        try {
            log.info("Closing Los data file." );
            losOs.close();
        } catch (IOException e) {
            log.error("Error while trying to close Los file: " + e.getMessage(), e);
        }
    }

    private void createLosDataFile(){

        path = Paths.get(System.getProperty("user.dir") + "/losData/" + "tm_" + losClock.getTimeStamp() + ".dat");
        try {
        	log.info("Creating Los file: {}", path);

            Files.createDirectories(path.getParent());
            Files.createFile(path);

            losOs = new FileOutputStream(path.toFile(),false);

        } catch (IOException e) {
            log.error("Error while creating los file: " +e.getMessage(), e);
        }
    }

}
