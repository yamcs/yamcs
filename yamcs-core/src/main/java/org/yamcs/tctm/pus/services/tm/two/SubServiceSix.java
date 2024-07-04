package org.yamcs.tctm.pus.services.tm.two;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.yarch.Bucket;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.YarchException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SubServiceSix implements PusSubService {
    String yamcsInstance;
    Log log;

    private static int REGISTER_ADDRESS_SIZE = 4;
    private static int REGISTER_VALUE_SIZE = 4;
    private static int DEFAULT_REPORT_COUNT_SIZE = 4;

    private static int registerAddressSize;
    private static int registerValueSize;
    private static int reportCountSize;

    Bucket registerDumpBucket;
    Gson gson;

    public SubServiceSix(String yamcsInstance, YConfiguration subServiceSixConfig) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        registerAddressSize = subServiceSixConfig.getInt("addressSize", REGISTER_ADDRESS_SIZE);
        registerValueSize = subServiceSixConfig.getInt("valueSize", REGISTER_VALUE_SIZE);
        reportCountSize = subServiceSixConfig.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);

        registerDumpBucket = PusTmManager.reports;

        try {
            registerDumpBucket.putObject("deviceRegisterReport/", "application/octet-stream", new HashMap<>(), new byte[0]);

        } catch (IOException e) {
            log.error("Unable to create a directory `" + registerDumpBucket.getName() + "reports/registerDumpBucket` for (Service - 2 | SubService - 6)", e);
            throw new YarchException("Failed to create a directory `" + registerDumpBucket.getName() + "reports/registerDumpBucket` for (Service - 2 | SubService - 6)", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int numberOfRegisters = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, reportCountSize);
        HashMap<Integer, Integer> registerValues = new HashMap<>(numberOfRegisters);

        for(int registerIndex = 0; registerIndex < numberOfRegisters; registerIndex++){
            int address = ByteArrayUtils.decodeInt(dataField, reportCountSize + registerIndex * (registerAddressSize + registerValueSize));
            int value = ByteArrayUtils.decodeInt(dataField, reportCountSize + registerIndex * (registerAddressSize + registerValueSize) + registerAddressSize);

            registerValues.put(address, value);
        }

        long missionTime = PusTmManager.timeService.getMissionTime();
        String filename = "deviceRegisterReport/" + LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        
        // Populate metadata
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("CreationTime", LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
        
        // Serialize the HashMap to JSONString
        String registerDump = gson.toJson(registerValues);

        // Save file to deviceRegisterReport bucket
        try {
            registerDumpBucket.putObject(filename, "json", metadata, registerDump.getBytes(StandardCharsets.UTF_8));
        } catch(IOException e) {
            throw new UncheckedIOException("S(2, 6) | Cannot save device register dump report in bucket: " + filename + (registerDumpBucket != null ? " -> " + registerDumpBucket.getName() : ""), e);
        }

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket); 

        return pPkts;
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
