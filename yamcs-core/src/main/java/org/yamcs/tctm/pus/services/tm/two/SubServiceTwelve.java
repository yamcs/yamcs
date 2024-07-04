package org.yamcs.tctm.pus.services.tm.two;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

public class SubServiceTwelve implements PusSubService {
    String yamcsInstance;
    Log log;

    private static int DEFAULT_DATA_ACQUISITION_CODE_SIZE = 2;
    private static int DEFAULT_AUXILLARY_DATA_SIZE = 4;
    private static int DEFAULT_PACT_ID_SIZE = 2;
    private static int DEFAULT_LOGICAL_DEVICE_ID_SIZE = 2;
    private static int DEFAULT_PARAMETER_ID_DATA_SIZE = 2;
    private static int DEFAULT_DATA_ACQUIRED_SIZE = 4;

    private static int dataAcquisitionCodeSize;
    private static int auxillaryDataSize;
    private static int logicalDeviceIDSize;
    private static int parameterIDDataSize;
    private static int pactIDSize;
    private static int dataAcquiredSize;
    private static int transactionIDSize = 4;

    Bucket logicalDeviceReportBucket;
    Gson gson;
    
    public SubServiceTwelve(String yamcsInstance, YConfiguration subServiceSixConfig) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        dataAcquisitionCodeSize = subServiceSixConfig.getInt("dataAcquisitionCodeSize", DEFAULT_DATA_ACQUISITION_CODE_SIZE);
        auxillaryDataSize = subServiceSixConfig.getInt("auxillaryDataSize", DEFAULT_AUXILLARY_DATA_SIZE);
        parameterIDDataSize = subServiceSixConfig.getInt("parameterIDDataSize", DEFAULT_PARAMETER_ID_DATA_SIZE);
        logicalDeviceIDSize = subServiceSixConfig.getInt("logicalDeviceIDSize", DEFAULT_LOGICAL_DEVICE_ID_SIZE);
        pactIDSize = subServiceSixConfig.getInt("pactIDSize", DEFAULT_PACT_ID_SIZE);
        dataAcquiredSize = subServiceSixConfig.getInt("dataAcquiredSize", DEFAULT_DATA_ACQUIRED_SIZE);

        logicalDeviceReportBucket = PusTmManager.reports;

        try {
            logicalDeviceReportBucket.putObject("logicalDeviceReport/", "application/octet-stream", new HashMap<>(), new byte[0]);

        } catch (IOException e) {
            log.error("Unable to create a directory `" + logicalDeviceReportBucket.getName() + "reports/logicalDeviceReport` for (Service - 2 | SubService - 12)", e);
            throw new YarchException("Failed to create a directory `" + logicalDeviceReportBucket.getName() + "reports/logicalDeviceReport` for (Service - 2 | SubService - 12)", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int transactionID = ByteArrayUtils.decodeInt(dataField, 0);
        int dataAcquisitionCode = ByteArrayUtils.decodeUnsignedShort(dataField, transactionIDSize);
        byte[] auxillaryData = Arrays.copyOfRange(dataField, (dataAcquisitionCodeSize + transactionIDSize), (dataAcquisitionCodeSize + transactionIDSize + auxillaryDataSize));
        int dataAcquired = ByteArrayUtils.decodeInt(dataField, (transactionIDSize + dataAcquisitionCodeSize + auxillaryDataSize));

        int pactID = ByteArrayUtils.decodeUnsignedShort(auxillaryData, 0);
        int logicalDeviceID = ByteArrayUtils.decodeUnsignedShort(auxillaryData, pactIDSize);
        int parameterIDData = ByteArrayUtils.decodeUnsignedShort(auxillaryData, (pactIDSize + logicalDeviceIDSize));

        long missionTime = PusTmManager.timeService.getMissionTime();
        String filename = "logicalDeviceReport/" + LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        
        // Populate metadata
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("CreationTime", LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
        

        // Save file to logicalDeviceReport bucket
        try {
            JSONArray logicalDeviceReportJSON = new JSONArray();
            byte[] logicalDeviceReport = logicalDeviceReportBucket.getObject(filename);

            if (logicalDeviceReport != null) {
                logicalDeviceReportJSON = new JSONArray(new String(logicalDeviceReport));
                logicalDeviceReportBucket.deleteObject(filename);
            }

            JSONObject currentDeviceReport = new JSONObject();
            currentDeviceReport.put("pactID", pactID);
            currentDeviceReport.put("logicalDeviceID", logicalDeviceID);
            currentDeviceReport.put("dataAcquisitionCode", dataAcquisitionCode);
            currentDeviceReport.put("transactionID", transactionID);
            currentDeviceReport.put("parameterIDData", parameterIDData);
            currentDeviceReport.put("dataAcquired", dataAcquired);

            logicalDeviceReportJSON.put(currentDeviceReport);
            logicalDeviceReportBucket.putObject(filename, "json", metadata, logicalDeviceReportJSON.toString().getBytes(StandardCharsets.UTF_8));

        } catch(IOException e) {
            throw new UncheckedIOException("S(2, 12) | Cannot save / update logical device ID dump report in bucket: " + filename + (logicalDeviceReportBucket != null ? " -> " + logicalDeviceReportBucket.getName() : ""), e);
        }

        ArrayList<TmPacket> pktList = new ArrayList<>();
        pktList.add(tmPacket);

        return pktList;
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }    
}
