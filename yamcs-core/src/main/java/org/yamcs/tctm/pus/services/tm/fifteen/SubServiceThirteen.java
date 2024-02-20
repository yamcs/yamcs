package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;

import java.util.ArrayList;

public class SubServiceThirteen implements PusSubService {
    String yamcsInstance;
    YConfiguration config;

    protected static int DEFAULT_TIMETAG_SIZE = 4;
    protected static int DEFAULT_PERCENTAGE_SIZE = 2;

    protected static int oldestStoredTimeSize;
    protected static int newestStoredTimeSize;
    protected static int openRetrievalStartTimetagSize;
    protected static int percentageFilledSize;
    protected static int fromOpenRetrievalPercentageFilledSize;


    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        oldestStoredTimeSize = config.getInt("oldestStoredTimeSize", DEFAULT_TIMETAG_SIZE);
        newestStoredTimeSize = config.getInt("newestStoredTimeSize", DEFAULT_TIMETAG_SIZE);
        openRetrievalStartTimetagSize = config.getInt("openRetrievalStartTimetagSize", DEFAULT_TIMETAG_SIZE);
        percentageFilledSize = config.getInt("percentageFilledSize", DEFAULT_PERCENTAGE_SIZE);
        fromOpenRetrievalPercentageFilledSize = config.getInt("fromOpenRetrievalPercentageFilledSize", DEFAULT_PERCENTAGE_SIZE);

    }
    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        ArrayList<TmPacket> pusPackets = new ArrayList<>();
        pusPackets.add(tmPacket);

        return pusPackets;
    }
}
