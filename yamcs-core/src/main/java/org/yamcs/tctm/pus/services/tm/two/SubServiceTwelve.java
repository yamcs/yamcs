package org.yamcs.tctm.pus.services.tm.two;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;

import org.yamcs.InitException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.TmContainer;
import org.yamcs.tctm.pus.services.tm.BucketSaveHandler;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

public class SubServiceTwelve extends BucketSaveHandler implements PusSubService {
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

        try {
            logicalDeviceReportBucket = getBucket("logicalDeviceReport", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `logicalDeviceReport bucket` for (Service - 2 | SubService - 12)", e);
            throw new YarchException("Failed to create RDB based bucket: logicalDeviceReport", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public TmPacket process(PusTmPacket pusTmPacket) {
        byte[] dataField = pusTmPacket.getDataField();

        int transactionID = ByteArrayUtils.decodeInt(dataField, 0);
        int dataAcquisitionCode = ByteArrayUtils.decodeUnsignedShort(dataField, transactionIDSize);
        byte[] auxillaryData = Arrays.copyOfRange(dataField, (dataAcquisitionCodeSize + transactionIDSize), (dataAcquisitionCodeSize + transactionIDSize + auxillaryDataSize));
        int dataAcquired = ByteArrayUtils.decodeInt(dataField, (transactionIDSize + dataAcquisitionCodeSize + auxillaryDataSize));

        int pactID = ByteArrayUtils.decodeUnsignedShort(auxillaryData, 0);
        int logicalDeviceID = ByteArrayUtils.decodeUnsignedShort(auxillaryData, pactIDSize);
        int parameterIDData = ByteArrayUtils.decodeUnsignedShort(auxillaryData, (pactIDSize + logicalDeviceIDSize));

        String logicalDeviceReportName = "";
        HashMap<String, String> logicalDeviceReportMetadata = new HashMap<>();

        // Save file to deviceRegisterDump bucket
        try {
            JSONArray logicalDeviceReportJSON = new JSONArray();
            byte[] logicalDeviceReport = logicalDeviceReportBucket.getObject(logicalDeviceReportName);

            if (logicalDeviceReport != null) {
                logicalDeviceReportJSON = new JSONArray(new String(logicalDeviceReport));
                logicalDeviceReportBucket.deleteObject(logicalDeviceReportName);
            }

            JSONObject currentDeviceReport = new JSONObject();
            currentDeviceReport.put("pactID", pactID);
            currentDeviceReport.put("logicalDeviceID", logicalDeviceID);
            currentDeviceReport.put("dataAcquisitionCode", dataAcquisitionCode);
            currentDeviceReport.put("transactionID", transactionID);
            currentDeviceReport.put("parameterIDData", parameterIDData);
            currentDeviceReport.put("dataAcquired", dataAcquired);

            logicalDeviceReportJSON.put(currentDeviceReport);
            logicalDeviceReportBucket.putObject(logicalDeviceReportName, "JSON", logicalDeviceReportMetadata, logicalDeviceReportJSON.toString().getBytes());

        } catch(IOException e) {
            throw new UncheckedIOException("Cannot save / update logical device ID dump report in bucket: " + logicalDeviceReportName + (logicalDeviceReportBucket != null ? " -> " + logicalDeviceReportBucket.getName() : ""), e);
        }

        TmPacket tmPacket = pusTmPacket.getTmPacket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try{
            outputStream.write(pusTmPacket.getPrimaryHeader());
            outputStream.write(pusTmPacket.getSecondaryHeader());
            outputStream.write(pusTmPacket.getDataField());
        } catch (IOException e) {
            // FIXME: Should never happen
        }

        tmPacket.addToContainerList(new TmContainer(outputStream.toByteArray()));
        return tmPacket;
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }    
}
