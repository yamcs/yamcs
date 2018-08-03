package org.yamcs.web.rest;

import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.protobuf.Mdb.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler;
import org.yamcs.web.rest.processor.ProcessorRestHandler;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;

public class YamcsToGpbAssembler {

    public static MissionDatabase toMissionDatabase(RestRequest req, String instanceName, XtceDb mdb) {
        YamcsServerInstance instance = YamcsServer.getInstance(instanceName);
        YamcsInstance instanceInfo = instance.getInstanceInfo();
        MissionDatabase.Builder b = MissionDatabase.newBuilder(instanceInfo.getMissionDatabase());
        SpaceSystem ss = mdb.getRootSpaceSystem();
        for (SpaceSystem sub : ss.getSubSystems()) {
            b.addSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(req, sub));
        }
        return b.build();
    }

    public static YamcsInstance enrichYamcsInstance(RestRequest req, YamcsInstance yamcsInstance) {
        YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);

        // Override MDB with a version that has URLs too
        if (yamcsInstance.hasMissionDatabase()) {
            XtceDb mdb = YamcsServer.getInstance(yamcsInstance.getName()).getXtceDb();
            if (mdb != null) {
                instanceb.setMissionDatabase(YamcsToGpbAssembler.toMissionDatabase(req, yamcsInstance.getName(), mdb));
            }
        }

        for (Processor processor : Processor.getProcessors(instanceb.getName())) {
            instanceb.addProcessor(ProcessorRestHandler.toProcessorInfo(processor, req, false));
        }

        TimeService timeService = YamcsServer.getTimeService(yamcsInstance.getName());
        instanceb.setMissionTime(TimeEncoding.toString(timeService.getMissionTime()));
        return instanceb.build();
    }

    public static ClientInfo toClientInfo(ConnectedClient client, ClientState state) {
        ClientInfo.Builder clientb = ClientInfo.newBuilder()
                .setApplicationName(client.getApplicationName())
                .setUsername(client.getUser().getUsername())
                .setLoginTime(client.getLoginTime())
                .setLoginTimeUTC(TimeEncoding.toString(client.getLoginTime()))
                .setId(client.getId())
                .setState(state);

        Processor processor = client.getProcessor();
        if (processor != null) {
            clientb.setInstance(processor.getInstance());
            clientb.setProcessorName(processor.getName());
        }
        return clientb.build();
    }
}
