package org.yamcs.tctm.pus.services.tm.two;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;

import org.yamcs.InitException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.tm.BucketSaveHandler;
import org.yamcs.tctm.pus.services.tm.PusTmModifier;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

public class SubServiceNine extends BucketSaveHandler implements PusSubService {
    String yamcsInstance;
    Log log;

    private static int DEFAULT_DATA_ACQUISITION_CODE_SIZE = 2;
    private static int DEFAULT_AUXILLARY_DATA_SIZE = 4;
    private static int DEFAULT_PACT_ID_SIZE = 2;
    private static int DEFAULT_PHYSICAL_DEVICE_ID_SIZE = 2;
    private static int DEFAULT_PROTOCOL_SPECIFIC_DATA_SIZE = 2;
    private static int DEFAULT_DATA_ACQUIRED_SIZE = 4;

    private static int dataAcquisitionCodeSize;
    private static int auxillaryDataSize;
    private static int physicalDeviceIDSize;
    private static int protocolSpecificDataSize;
    private static int pactIDSize;
    private static int dataAcquiredSize;
    private static int transactionIDSize = 4;

    Bucket physicalDeviceReportBucket;
    Gson gson;
    
    public SubServiceNine(String yamcsInstance, YConfiguration subServiceSixConfig) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        dataAcquisitionCodeSize = subServiceSixConfig.getInt("dataAcquisitionCodeSize", DEFAULT_DATA_ACQUISITION_CODE_SIZE);
        auxillaryDataSize = subServiceSixConfig.getInt("auxillaryDataSize", DEFAULT_AUXILLARY_DATA_SIZE);
        protocolSpecificDataSize = subServiceSixConfig.getInt("protocolSpecificDataSize", DEFAULT_PROTOCOL_SPECIFIC_DATA_SIZE);
        physicalDeviceIDSize = subServiceSixConfig.getInt("physicalDeviceIDSize", DEFAULT_PHYSICAL_DEVICE_ID_SIZE);
        pactIDSize = subServiceSixConfig.getInt("pactIDSize", DEFAULT_PACT_ID_SIZE);
        dataAcquiredSize = subServiceSixConfig.getInt("dataAcquiredSize", DEFAULT_DATA_ACQUIRED_SIZE);

        try {
            physicalDeviceReportBucket = getBucket("physicalDeviceReport", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `physicalDeviceReport bucket` for (Service - 2 | SubService - 9)", e);
            throw new YarchException("Failed to create RDB based bucket: physicalDeviceReport", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] dataField = PusTmModifier.getDataField(tmPacket);

        int transactionID = ByteArrayUtils.decodeInt(dataField, 0);
        int dataAcquisitionCode = ByteArrayUtils.decodeUnsignedShort(dataField, transactionIDSize);
        byte[] auxillaryData = Arrays.copyOfRange(dataField, (dataAcquisitionCodeSize + transactionIDSize), (dataAcquisitionCodeSize + transactionIDSize + auxillaryDataSize));
        int dataAcquired = ByteArrayUtils.decodeInt(dataField, (transactionIDSize + dataAcquisitionCodeSize + auxillaryDataSize));

        int pactID = ByteArrayUtils.decodeUnsignedShort(auxillaryData, 0);
        int physicalDeviceID = ByteArrayUtils.decodeUnsignedShort(auxillaryData, pactIDSize);
        int protocolSpecificData = ByteArrayUtils.decodeUnsignedShort(auxillaryData, (pactIDSize + physicalDeviceIDSize));

        String physicalDeviceReportName = "";
        HashMap<String, String> physicalDeviceReportMetadata = new HashMap<>();

        // Save file to deviceRegisterDump bucket
        try {
            JSONArray physicalDeviceReportJSON = new JSONArray();
            byte[] physicalDeviceReport = physicalDeviceReportBucket.getObject(physicalDeviceReportName);

            if (physicalDeviceReport != null) {
                physicalDeviceReportJSON = new JSONArray(new String(physicalDeviceReport));
                physicalDeviceReportBucket.deleteObject(physicalDeviceReportName);
            }

            JSONObject currentDeviceReport = new JSONObject();
            currentDeviceReport.put("pactID", pactID);
            currentDeviceReport.put("physicalDeviceID", physicalDeviceID);
            currentDeviceReport.put("dataAcquisitionCode", dataAcquisitionCode);
            currentDeviceReport.put("transactionID", transactionID);
            currentDeviceReport.put("protocolSpecificData", protocolSpecificData);
            currentDeviceReport.put("dataAcquired", dataAcquired);

            physicalDeviceReportJSON.put(currentDeviceReport);
            physicalDeviceReportBucket.putObject(physicalDeviceReportName, "JSON", physicalDeviceReportMetadata, physicalDeviceReportJSON.toString().getBytes());

        } catch(IOException e) {
            throw new UncheckedIOException("Cannot save / update physical device ID dump report in bucket: " + physicalDeviceReportName + (physicalDeviceReportBucket != null ? " -> " + physicalDeviceReportBucket.getName() : ""), e);
        }

        return tmPacket;
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }    
}
