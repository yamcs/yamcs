package org.yamcs.web.rest;

import org.yamcs.YProcessor;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler;
import org.yamcs.web.rest.processor.ProcessorRestHandler;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class YamcsToGpbAssembler {
    
    public static MissionDatabase toMissionDatabase(RestRequest req, String instance, XtceDb mdb) {
        YamcsInstance yamcsInstance = YamcsServer.getYamcsInstance(instance);
        MissionDatabase.Builder b = MissionDatabase.newBuilder(yamcsInstance.getMissionDatabase());
        if (!req.getOptions().contains(Option.NO_LINK)) {
            String apiUrl = req.getApiURL();
            b.setUrl(apiUrl + "/mdb/" + instance);
            b.setParametersUrl(b.getUrl() + "/parameters{/namespace}{/name}");
            b.setContainersUrl(b.getUrl() + "/containers{/namespace}{/name}");
            b.setCommandsUrl(b.getUrl() + "/commands{/namespace}{/name}");
            b.setAlgorithmsUrl(b.getUrl() + "/algorithms{/namespace}{/name}");
        }
        
        SpaceSystem ss = mdb.getRootSpaceSystem();
        for (SpaceSystem sub : ss.getSubSystems()) {
            b.addSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(req, instance, sub));
        }        
        return b.build();
    }
    
    public static YamcsInstance enrichYamcsInstance(RestRequest req, YamcsInstance yamcsInstance) {
        YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);
        
        // Override MDB with a version that has URLs too
        if (yamcsInstance.hasMissionDatabase()) {
            XtceDb mdb = XtceDbFactory.getInstance(yamcsInstance.getName());
            instanceb.setMissionDatabase(YamcsToGpbAssembler.toMissionDatabase(req, yamcsInstance.getName(), mdb)); 
        }
        
        if (!req.getOptions().contains(Option.NO_LINK)) {
            String apiUrl = req.getApiURL();
            String instanceUrl = apiUrl + "/instances/" + instanceb.getName();
            instanceb.setUrl(instanceUrl);
            instanceb.setEventsUrl(instanceUrl + "{/processor}/events");
            instanceb.setClientsUrl(instanceUrl + "{/processor}/clients");
        }
        
        for (YProcessor processor : YProcessor.getProcessors(instanceb.getName())) {
            instanceb.addProcessor(ProcessorRestHandler.toProcessorInfo(processor, req, false));
        }
        return instanceb.build();
    }
}
