package org.yamcs.tctm.pus.services.tm.five;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;

public class SubServiceEight implements PusSubService {
    String yamcsInstance;
    Log log;

    private int DEFAULT_EVENT_DEFINITION_ID_SIZE = 4;
    private int DEFAULT_INTEGER_SIZE = 4;

    private int eventDefinitionIDSize;

    Bucket disabledEventDefinitionsListBucket;

    public SubServiceEight(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        eventDefinitionIDSize = config.getInt("eventDefinitionIDSize", DEFAULT_EVENT_DEFINITION_ID_SIZE);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        byte[] eventDefinitionsIDsArr = Arrays.copyOfRange(dataField, DEFAULT_INTEGER_SIZE, dataField.length);
        int numberOfEventDefinitions = ByteArrayUtils.decodeInt(dataField, 0);

        ArrayList<Integer> eventDefinitionIDs = new ArrayList<Integer>(numberOfEventDefinitions);
        for(int index = 0; index < numberOfEventDefinitions; index++) {
            int eventDefinitionID = (int) ByteArrayUtils.decodeCustomInteger(eventDefinitionsIDsArr,(index * eventDefinitionIDSize), eventDefinitionIDSize);
            eventDefinitionIDs.add(eventDefinitionID);
        }

        JSONArray eventDefinitionsIDsJsonArr = new JSONArray();
        for (int eventDefinitionID : eventDefinitionIDs) {
            eventDefinitionsIDsJsonArr.put(eventDefinitionID);
        }

        JSONObject eventDefinitionIDJsonObj = new JSONObject();
        eventDefinitionIDJsonObj.put("count", numberOfEventDefinitions);
        eventDefinitionIDJsonObj.put("disabledEventDefinitionIDs", eventDefinitionsIDsJsonArr);

        String eventDefinitionIDJsonString = eventDefinitionIDJsonObj.toString(2);

        // FIXME: What should the filename be? What should the metadata be?
        String disabledEventDefinitionIDListFileName = "";
        HashMap<String, String> disabledEventDefinitionIDListMetadata = new HashMap<>();

        // Save file to disabledEventDefinitionsListBucket bucket
        try {
            disabledEventDefinitionsListBucket.putObject(disabledEventDefinitionIDListFileName, "JSON", disabledEventDefinitionIDListMetadata, eventDefinitionIDJsonString.getBytes());

        } catch(IOException e) {
            throw new UncheckedIOException("Cannot save disabled event definitions IDs report in bucket: " + disabledEventDefinitionIDListFileName + (disabledEventDefinitionsListBucket != null ? " -> " + disabledEventDefinitionsListBucket.getName() : ""), e);
        }

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);

        return pPkts; // FIXME: This returns null because the PUS packages carved out have the same
                      // (gentime, apidseqcount), which means they cannot all be archived by the
                      // XtceTmRecorder nor processed by the StreamTmPacketProvider
    }
}
