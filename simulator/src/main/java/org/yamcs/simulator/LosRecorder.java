package org.yamcs.simulator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates the storing and retrieving of TM during an LOS (Loss of Signal).
 */
public class LosRecorder {

    private static final Logger log = LoggerFactory.getLogger(LosRecorder.class);

    private File dataDir;

    private OutputStream losOs;
    private File currentFile;

    public LosRecorder(File dataDir) {
        this.dataDir = dataDir;
    }

    public void startRecording(Date start) {
        String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(start);
        currentFile = new File(dataDir, "tm_" + timestamp + ".dat");
        try {
            log.info("Creating LOS dump: {}", currentFile);
            Files.createFile(currentFile.toPath());

            losOs = new FileOutputStream(currentFile, false);
        } catch (IOException e) {
            log.error("Error while creating LOS dump: " + e.getMessage(), e);
        }
    }

    public void stopRecording() {
        if (losOs != null) {
            try {
                log.info("Closing LOS recording.");
                losOs.close();
            } catch (IOException e) {
                log.error("Could not close LOS recording: " + e.getMessage(), e);
            }
        }
    }

    public String[] listRecordings() {
        return Arrays.asList(dataDir.listFiles()).stream()
                .map(File::getName)
                .toArray(String[]::new);
    }

    public InputStream getInputStream(String recordingName) throws IOException {
        return new FileInputStream(new File(dataDir, recordingName));
    }

    public void record(SimulatorCcsdsPacket packet) {
        try {
            losOs.write(packet.getBytes());
        } catch (IOException e) {
            log.error("Could not record packet: " + e.getMessage(), e);
        }
    }

    public void deleteDump(String name) {
        File file = new File(dataDir, name);
        file.delete();
    }

    public String getCurrentRecordingName() {
        if (currentFile == null) {
            return null;
        }
        return currentFile.getName();
    }
}
