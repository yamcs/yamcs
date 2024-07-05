package org.yamcs.tctm.pus.services.tm.eleven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import com.google.gson.Gson;


public class SubServiceThirteen implements PusSubService {
    String yamcsInstance;
    Log log;
    private static int requestIdSize;

    private static int DEFAULT_UNIQUE_SIGNATURE_OFFSET = 2;

    private static int DEFAULT_UNIQUE_SIGNATURE_SIZE = 4;
    private static int DEFAULT_REPORT_INDEX_SIZE = 1;
    private static int DEFAULT_REPORT_COUNT_SIZE = 1;

    protected int uniqueSignatureOffset;
    protected int uniqueSignatureSize;
    protected int reportIndexSize;
    protected int reportCountSize;

    Bucket timetagScheduleSummaryReportBucket;

    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        uniqueSignatureOffset = config.getInt("tcIndexOffset", DEFAULT_UNIQUE_SIGNATURE_OFFSET);
        uniqueSignatureSize = config.getInt("tcIndexSize", DEFAULT_UNIQUE_SIGNATURE_SIZE);
        reportIndexSize = config.getInt("reportIndexSize", DEFAULT_REPORT_INDEX_SIZE);
        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);

        requestIdSize = ServiceEleven.sourceIdSize + ServiceEleven.apidSize + ServiceEleven.seqCountSize;
        timetagScheduleSummaryReportBucket = PusTmManager.reports;

        try {
            timetagScheduleSummaryReportBucket.putObject(yamcsInstance + "/timetagScheduleSummaryReport/", "application/octet-stream", new HashMap<>(), new byte[0]);

        } catch (IOException e) {
            log.error("Unable to create a directory `" + timetagScheduleSummaryReportBucket.getName() + "/timetagScheduleSummaryReport` for (Service - 11 | SubService - 13)", e);
            throw new YarchException("Failed to create a directory `" + timetagScheduleSummaryReportBucket.getName() + "/timetagScheduleSummaryReport` for (Service - 11 | SubService - 13)", e);
        }
    }

    public ObjectProperties findObject(int uniqueSignature) throws IOException {
        List<ObjectProperties> fileObjects = timetagScheduleSummaryReportBucket.listObjects();
        for (ObjectProperties prop: fileObjects) {
            Map<String, String> metadata = prop.getMetadataMap();

            if (metadata != null) {
                String sig = metadata.get("UniqueSignature");
                if (sig != null) {
                    int signature = Integer.parseInt(sig);
                    if(signature == uniqueSignature)
                        return prop;
                }
            }
        }
        return null;
    }

    public void generateTimetagScheduleSummaryReport(long gentime, Map<Long, ArrayList<Integer>> requestTcPacketsMap, Map<String, Integer> props, ObjectProperties foundObject) {
        long missionTime = PusTmManager.timeService.getMissionTime();

        String filename;
        String content;
        Map<String, String> metadata;

        if (foundObject == null) {
            filename = yamcsInstance + "/timetagScheduleSummaryReport/" + LocalDateTime.ofInstant(
                Instant.ofEpochSecond(gentime),
                ZoneId.of("GMT")
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")) + ".csv";

            // Populate metadata
            metadata = new HashMap<>();
            metadata.put("CreationTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(missionTime),
                ZoneId.of("GMT")
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

            // Populate modified time
            metadata.put("ModifiedTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(missionTime),
                ZoneId.of("GMT")
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

            // Populate properties
            for (Map.Entry<String, Integer> prop: props.entrySet()) {
                if (prop.getKey() == "ReportIndex") {
                    List<String> indices = new ArrayList<>();

                    indices.add(String.valueOf(prop.getValue()));
                    metadata.put("ReportIndices", new Gson().toJson(indices));
                    continue;
                }
                metadata.put(prop.getKey(), prop.getValue().toString());
            }

            try (StringWriter stringWriter = new StringWriter();
                BufferedWriter writer = new BufferedWriter(stringWriter)) {

                // Write header
                writer.write("ReleaseTimetag,SourceId,CommandApid,CommandCcsdsSeqCount");
                writer.newLine();
                writer.flush();
                
                content = stringWriter.getBuffer().toString();

            } catch (IOException e) {
                throw new UncheckedIOException("S(11, 13) | Cannot save timetag summary report in bucket: " + filename + " " + (timetagScheduleSummaryReportBucket != null ? " -> " + timetagScheduleSummaryReportBucket.getName() : ""), e);
            }

        } else {
            metadata = new HashMap<>(foundObject.getMetadataMap());
            filename = foundObject.getName();

            // Populate modified time
            metadata.put("ModifiedTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(missionTime),
                ZoneId.of("GMT")
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

            // Update Report indices in metadata
            @SuppressWarnings("unchecked")
            List<String> indices = new Gson().fromJson(metadata.get("ReportIndices"), ArrayList.class);

            indices.add(String.valueOf(props.get("ReportIndex")));
            metadata.put("ReportIndices", new Gson().toJson(indices));

            try {
                // Fetch content from foundObject
                content = new String(timetagScheduleSummaryReportBucket.getObject(filename), StandardCharsets.UTF_8);

                // Delete File
                timetagScheduleSummaryReportBucket.deleteObject(filename);

            } catch (IOException e) {
                throw new UncheckedIOException("S(11, 13) | Cannot delete previous timetag summary report in bucket: " + filename + " " + (timetagScheduleSummaryReportBucket != null ? " -> " + timetagScheduleSummaryReportBucket.getName() : ""), e);
            }
        }

        try (StringWriter stringWriter = new StringWriter();
            BufferedWriter writer = new BufferedWriter(stringWriter)) {

            // Add content
            writer.write(content);

            for(Map.Entry<Long, ArrayList<Integer>> requestTcMap: requestTcPacketsMap.entrySet()) {
                ArrayList<Integer> requestId = requestTcMap.getValue();

                String timetagStr = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(requestTcMap.getKey()),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

                int sourceId = requestId.get(0);
                int commandApid = requestId.get(1);
                int commandCcsdsSeqCount = requestId.get(2);

                writer.write(timetagStr + "," + sourceId + "," + commandApid + "," + commandCcsdsSeqCount);
                writer.newLine();
            }
            writer.flush();

            // Put report in the bucket
            timetagScheduleSummaryReportBucket.putObject(filename, "csv", metadata, stringWriter.getBuffer().toString().getBytes(StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new UncheckedIOException("S(11, 13) | Cannot save timetag summary report in bucket: " + filename + " " + (timetagScheduleSummaryReportBucket != null ? " -> " + timetagScheduleSummaryReportBucket.getName() : ""), e);
        }
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();
        byte[] spareField = pPkt.getSpareField();

        Map<String, Integer> props = new HashMap<>();
        int uniqueSignature = (int) ByteArrayUtils.decodeCustomInteger(spareField, uniqueSignatureOffset, uniqueSignatureSize);
        int reportIndex = (int) ByteArrayUtils.decodeCustomInteger(spareField, uniqueSignatureOffset + uniqueSignatureSize, reportIndexSize);
        int reportCount = (int) ByteArrayUtils.decodeCustomInteger(spareField, uniqueSignatureOffset + uniqueSignatureSize + reportIndexSize, reportCountSize);

        props.put("UniqueSignature", uniqueSignature);
        props.put("ReportIndex", reportIndex);
        props.put("ReportCount", reportCount);

        int numOfReports = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceEleven.reportCountSize);
        byte[] reportArr = Arrays.copyOfRange(dataField, ServiceEleven.reportCountSize, dataField.length);

        Map<Long, ArrayList<Integer>> requestTcPacketsMap = new HashMap<>(numOfReports);

        for (int index = 0; index < numOfReports; index++) {
            long releaseTime = ByteArrayUtils.decodeCustomInteger(reportArr, 0, PusTcManager.timetagLength);
            ArrayList<Integer> tcIdentification = extractFromRequestId(reportArr);

            requestTcPacketsMap.put(releaseTime, tcIdentification);
            reportArr = Arrays.copyOfRange(reportArr, PusTcManager.timetagLength + requestIdSize, reportArr.length);
        }

        // Check if a unique file already exists
        try {
            ObjectProperties foundObject = findObject(uniqueSignature);
            long generationTime = ByteArrayUtils.decodeCustomInteger(pPkt.getGenerationTime(), 0, PusTmManager.absoluteTimeLength);

            // Generate the report
            generateTimetagScheduleSummaryReport(generationTime, requestTcPacketsMap, props, foundObject);

        } catch (IOException e) {
            throw new UncheckedIOException("S(11, 13) | Unable to find object with UniqueSignature: " + uniqueSignature + " in bucket: " + (timetagScheduleSummaryReportBucket != null ? " -> " + timetagScheduleSummaryReportBucket.getName() : ""), e);
        }

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);
        
        return pPkts;
    }

    private static ArrayList<Integer> extractFromRequestId(byte[] reportArr) {
        byte[] requestIdArr = Arrays.copyOfRange(reportArr, PusTcManager.timetagLength, PusTcManager.timetagLength + requestIdSize);

        ArrayList<Integer> requestId = new ArrayList<>();
        requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, 0, ServiceEleven.sourceIdSize));
        requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, ServiceEleven.sourceIdSize, ServiceEleven.apidSize));
        requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, (ServiceEleven.sourceIdSize + ServiceEleven.apidSize), ServiceEleven.seqCountSize));
        return requestId;
    }
}
