package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.InitException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;
import org.yamcs.tctm.pus.services.tm.BucketSaveHandler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SubServiceThirteen extends BucketSaveHandler implements PusSubService {
    String yamcsInstance;
    Log log;

    private int DEFAULT_TIMETAG_SIZE = 4;
    private int DEFAULT_PERCENTAGE_SIZE = 2;
    private int DEFAULT_REPORT_COUNT_SIZE = 1;
    private int DEFAULT_PACKET_STORE_ID_SIZE = 1;

    protected int storedTimeSize;
    protected int openRetrievalStartTimetagSize;
    protected int percentageFilledSize;
    protected int fromOpenRetrievalPercentageFilledSize;
    protected int reportCountSize;
    protected int packetStoreIdSize;

    Bucket packetStoreSummaryReportBucket;
    TimeService timeService;

    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        packetStoreIdSize = config.getInt("packetStoreIdSize", DEFAULT_PACKET_STORE_ID_SIZE);
        storedTimeSize = config.getInt("storedTimeSize", DEFAULT_TIMETAG_SIZE);
        openRetrievalStartTimetagSize = config.getInt("openRetrievalStartTimetagSize", DEFAULT_TIMETAG_SIZE);
        percentageFilledSize = config.getInt("percentageFilledSize", DEFAULT_PERCENTAGE_SIZE);
        fromOpenRetrievalPercentageFilledSize = config.getInt("fromOpenRetrievalPercentageFilledSize", DEFAULT_PERCENTAGE_SIZE);
        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);

        try {
            packetStoreSummaryReportBucket = getBucket("packetStoreSummaryReportBucket", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `packetStoreSummaryReportBucket bucket` for (Service - 15 | SubService - 13)", e);
            throw new YarchException("Failed to create RDB based bucket: packetStoreSummaryReportBucket | (Service - 15 | SubService - 13)", e);
        }

        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    public void generatePacketStoredSummaryReport(Map<Integer, byte[]> packetStoreReportMap) {
        long missionTime = timeService.getMissionTime();
        String packetStoreSummaryReportName = "PacketStoreSummaryReport | " + LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));
        

        try (StringWriter stringWriter = new StringWriter();
            BufferedWriter writer = new BufferedWriter(stringWriter)) {

            // Write header
            writer.write("PacketStoreID, OldestStoredPacketTime, NewestStoredPacketTime, CurrentOpenRetrievalStartTimetag, PercentageFilled, FromOpenRetrievalStartTimetagPercentageFilled\n");

            for (Map.Entry<Integer, byte[]> packetStoreReport: packetStoreReportMap.entrySet()) {
                byte[] report = packetStoreReport.getValue();
                int packetStoreId = packetStoreReport.getKey();

                String oldestStoredTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ByteArrayUtils.decodeCustomInteger(report, 0, storedTimeSize)),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));
                String newestStoredTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ByteArrayUtils.decodeCustomInteger(report, storedTimeSize, storedTimeSize)),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));
                String currentTimetag = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ByteArrayUtils.decodeCustomInteger(report, storedTimeSize * 2, openRetrievalStartTimetagSize)),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));
                int percentageFilled = (int) ByteArrayUtils.decodeCustomInteger(report, storedTimeSize * 2 + openRetrievalStartTimetagSize, percentageFilledSize);
                int fromPercentageFilled = (int) ByteArrayUtils.decodeCustomInteger(report, storedTimeSize * 2 + openRetrievalStartTimetagSize + percentageFilledSize, fromOpenRetrievalPercentageFilledSize);

                writer.write(packetStoreId + ", " + oldestStoredTime + ", " + newestStoredTime + ", " + currentTimetag + ", " + percentageFilled + ", " + fromPercentageFilled + "\n");
            }

            // Put report in the bucket
            packetStoreSummaryReportBucket.putObject(packetStoreSummaryReportName, "csv", new HashMap<>(), stringWriter.toString().getBytes());

        } catch (IOException e) {
            e.printStackTrace();
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
        generatePacketStoredSummaryReport(packetStoreReportMap);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket); 

        return pPkts;
    }
}
