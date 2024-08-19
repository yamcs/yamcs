package org.yamcs.tctm.pus.services.tm.five;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.tctm.pus.services.tm.five.ServiceFive.Endianess;
import org.yamcs.tctm.pus.services.tm.one.ServiceOne;
import org.yamcs.tctm.pus.tuples.Pair;
import org.yamcs.tctm.pus.tuples.Quattro;
import org.yamcs.utils.ByteArrayUtils;

public class SubServiceOne implements PusSubService {
    String yamcsInstance;
    Log log;

    EventProducer eventProducer;

    static String source = "Service: 5 | SubService: 1";

    public SubServiceOne(String yamcsInstance, YConfiguration subServiceSixConfig) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int apid = pPkt.getAPID();
        int eventId = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceFive.eventIdSize);

        Pair<String, Map<Integer, Quattro<Integer, String, Endianess, Map<Integer, Pair<Integer, String>>>>> eventMap = ServiceFive.eventIds.get(new Pair<>(apid, eventId));
        if(eventMap == null) {
            log.error("Invalid APID, Event ID for S5,1 packet: {}, {}", apid, eventId);
            ArrayList<TmPacket> pPkts = new ArrayList<>();
            pPkts.add(tmPacket);
    
            return pPkts;
        }

        Map<Integer, Quattro<Integer, String, Endianess, Map<Integer, Pair<Integer, String>>>> chunkMap = eventMap.getSecond();

        String eventDec = "EventName: " + eventMap.getFirst() + " | EventId: " + eventId;

        if (chunkMap != null) {
            for (Map.Entry<Integer, Quattro<Integer, String, Endianess, Map<Integer, Pair<Integer, String>>>> chunk: chunkMap.entrySet()) {
                Quattro<Integer, String, Endianess, Map<Integer, Pair<Integer, String>>> chunkDeets = chunk.getValue();

                int chunkOffset = chunk.getKey();
                int chunkLength = chunkDeets.getFirst();
                String chunkName = chunkDeets.getSecond();
                Endianess en = chunkDeets.getThird();
                Map<Integer, Pair<Integer, String>> bitsMap = chunkDeets.getFourth();

                if (bitsMap != null) {
                    long data = ByteArrayUtils.decodeCustomInteger(dataField, chunkOffset + ServiceFive.eventIdSize, chunkLength);

                    for (Map.Entry<Integer, Pair<Integer, String>> bits: bitsMap.entrySet()) {
                        long mask = ServiceFive.createOnes(chunkLength);
                        Pair<Integer, String> bitsDeets = bits.getValue();

                        int bitLength = bitsDeets.getFirst();
                        int bitOffset = bits.getKey() + bitLength;
                        String bitName = bitsDeets.getSecond();

                        // Generate bitMask
                        mask = ((~(mask >> bitOffset)) << (bitOffset - bitLength)) >> (bitOffset - bitLength);

                        long disValue = data & mask;
                        eventDec += " | " + bitName + ": " + disValue;
                    }
                    continue;
                }

                byte[] dataDar = Arrays.copyOfRange(dataField, chunkOffset + ServiceFive.eventIdSize, chunkOffset + ServiceFive.eventIdSize + chunkLength);
                if (en.equals(Endianess.LE))
                    dataDar = ServiceFive.convertEndian(dataDar);

                long data = ByteArrayUtils.decodeCustomInteger(dataDar, 0, chunkLength);
                eventDec += " | " + chunkName + ": " + data;
            }
        }

        eventDec += " is thrown";
        eventProducer.sendWatch(ServiceOne.CcsdsApid.fromValue(apid).name(), eventDec);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);

        return pPkts;
    }
}
