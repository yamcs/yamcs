package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SubServiceThirteen implements PusSubService {
    String yamcsInstance;
    Log log;

    private int DEFAULT_TIMETAG_SIZE = 4;
    private int DEFAULT_PERCENTAGE_SIZE = 1;
    private int DEFAULT_REPORT_COUNT_SIZE = 1;
    private int DEFAULT_PACKET_STORE_ID_SIZE = 1;

    protected int storedTimeSize;
    protected int openRetrievalStartTimetagSize;
    protected int percentageFilledSize;
    protected int fromOpenRetrievalPercentageFilledSize;
    protected int reportCountSize;
    protected int packetStoreIdSize;

    Bucket packetStoreSummaryReportBucket;

    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        packetStoreIdSize = config.getInt("packetStoreIdSize", DEFAULT_PACKET_STORE_ID_SIZE);
        storedTimeSize = config.getInt("storedTimeSize", DEFAULT_TIMETAG_SIZE);
        openRetrievalStartTimetagSize = config.getInt("openRetrievalStartTimetagSize", DEFAULT_TIMETAG_SIZE);
        percentageFilledSize = config.getInt("percentageFilledSize", DEFAULT_PERCENTAGE_SIZE);
        fromOpenRetrievalPercentageFilledSize = config.getInt("fromOpenRetrievalPercentageFilledSize", DEFAULT_PERCENTAGE_SIZE);
        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);

        packetStoreSummaryReportBucket = PusTmManager.reports;

        try {
            packetStoreSummaryReportBucket.putObject(yamcsInstance + "/packetStoreSummaryReport/", "application/octet-stream", new HashMap<>(), new byte[0]);

        } catch (IOException e) {
            log.error("Unable to create a directory `" + packetStoreSummaryReportBucket.getName() + "/packetStoreSummaryReport` for (Service - 15 | SubService - 13)", e);
            throw new YarchException("Failed to create a directory `" + packetStoreSummaryReportBucket.getName() + "/packetStoreSummaryReport` for (Service - 15 | SubService - 13)", e);
        }
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    public void generatePacketStoredSummaryReport(long generationTime, Map<Integer, byte[]> packetStoreReportMap) {
        long missionTime = PusTmManager.timeService.getMissionTime();
        String filename = yamcsInstance + "/packetStoreSummaryReport/" + LocalDateTime.ofInstant(
            Instant.ofEpochSecond(generationTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")) + ".csv";

        // Populate metadata
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("CreationTime", LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

        try (StringWriter stringWriter = new StringWriter();
            BufferedWriter writer = new BufferedWriter(stringWriter)) {

            // Write header
            writer.write("PacketStoreID,OldestStoredPacketTime,NewestStoredPacketTime,CurrentOpenRetrievalStartTimetag,PercentageFilled,FromOpenRetrievalStartTimetagPercentageFilled");
            writer.newLine();

            for (Map.Entry<Integer, byte[]> packetStoreReport: packetStoreReportMap.entrySet()) {
                byte[] report = packetStoreReport.getValue();
                int packetStoreId = packetStoreReport.getKey();

                String oldestStoredTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ByteArrayUtils.decodeCustomInteger(report, 0, storedTimeSize)),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
                String newestStoredTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ByteArrayUtils.decodeCustomInteger(report, storedTimeSize, storedTimeSize)),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
                String currentTimetag = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ByteArrayUtils.decodeCustomInteger(report, storedTimeSize * 2, openRetrievalStartTimetagSize)),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
                int percentageFilled = (int) ByteArrayUtils.decodeCustomInteger(report, storedTimeSize * 2 + openRetrievalStartTimetagSize, percentageFilledSize);
                int fromPercentageFilled = (int) ByteArrayUtils.decodeCustomInteger(report, storedTimeSize * 2 + openRetrievalStartTimetagSize + percentageFilledSize, fromOpenRetrievalPercentageFilledSize);

                writer.write(packetStoreId + "," + oldestStoredTime + "," + newestStoredTime + "," + currentTimetag + "," + percentageFilled + "," + fromPercentageFilled);
                writer.newLine();
            }
            writer.flush();

            // Put report in the bucket
            packetStoreSummaryReportBucket.putObject(filename, "csv", metadata, stringWriter.getBuffer().toString().getBytes(StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new UncheckedIOException("S(15, 13) | Cannot save packet store summary report in bucket: " + filename + (packetStoreSummaryReportBucket != null ? " -> " + packetStoreSummaryReportBucket.getName() : ""), e);
        }
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int numberOfReports = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, reportCountSize);

        Map<Integer, byte[]> packetStoreReportMap = new HashMap<>();
        int reportSize = packetStoreIdSize + storedTimeSize * 2 + openRetrievalStartTimetagSize + percentageFilledSize + fromOpenRetrievalPercentageFilledSize; 
        for(int registerIndex = 0; registerIndex < numberOfReports; registerIndex++){
            int packetStoreId = (int) ByteArrayUtils.decodeCustomInteger(dataField, reportCountSize + registerIndex * reportSize, packetStoreIdSize);
            byte[] data = Arrays.copyOfRange(dataField, reportCountSize + packetStoreIdSize + registerIndex * reportSize, reportCountSize + (registerIndex + 1) * reportSize);

            packetStoreReportMap.put(packetStoreId, data);
        }

        // Generate CSV report
        long generationTime = ByteArrayUtils.decodeCustomInteger(pPkt.getGenerationTime(), 0, PusTmManager.absoluteTimeLength);
        generatePacketStoredSummaryReport(generationTime, packetStoreReportMap);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket); 

        return pPkts;
    }
}
