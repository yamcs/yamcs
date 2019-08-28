package org.yamcs.http.api;

import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.http.api.processor.ProcessorRestHandler;
import org.yamcs.protobuf.ClientInfo;
import org.yamcs.protobuf.ClientInfo.ClientState;
import org.yamcs.protobuf.Mdb.MissionDatabase;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;

public class YamcsToGpbAssembler {

    public static MissionDatabase toMissionDatabase(String instanceName, XtceDb mdb) {
        YamcsServerInstance instance = YamcsServer.getServer().getInstance(instanceName);
        YamcsInstance instanceInfo = instance.getInstanceInfo();
        MissionDatabase.Builder b = MissionDatabase.newBuilder(instanceInfo.getMissionDatabase());
        b.setParameterCount(mdb.getParameters().size());
        b.setContainerCount(mdb.getSequenceContainers().size());
        b.setCommandCount(mdb.getMetaCommands().size());
        b.setAlgorithmCount(mdb.getAlgorithms().size());
        b.setParameterTypeCount(mdb.getParameterTypes().size());
        SpaceSystem ss = mdb.getRootSpaceSystem();
        for (SpaceSystem sub : ss.getSubSystems()) {
            b.addSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(sub));
        }
        return b.build();
    }

    public static YamcsInstance enrichYamcsInstance(YamcsInstance yamcsInstance) {
        YamcsInstance.Builder instanceb = YamcsInstance.newBuilder(yamcsInstance);

        // Override MDB with a version that has URLs too
        if (yamcsInstance.hasMissionDatabase()) {
            XtceDb mdb = YamcsServer.getServer().getInstance(yamcsInstance.getName()).getXtceDb();
            if (mdb != null) {
                instanceb.setMissionDatabase(YamcsToGpbAssembler.toMissionDatabase(yamcsInstance.getName(), mdb));
            }
        }

        for (Processor processor : Processor.getProcessors(instanceb.getName())) {
            instanceb.addProcessor(ProcessorRestHandler.toProcessorInfo(processor, false));
        }

        TimeService timeService = YamcsServer.getTimeService(yamcsInstance.getName());
        if (timeService != null) {
            instanceb.setMissionTime(TimeEncoding.toString(timeService.getMissionTime()));
        }
        return instanceb.build();
    }

    public static ClientInfo toClientInfo(ConnectedClient client, ClientState state) {
        ClientInfo.Builder clientb = ClientInfo.newBuilder()
                .setApplicationName(client.getApplicationName())
                .setAddress(client.getAddress())
                .setUsername(client.getUser().getName())
                .setId(client.getId())
                .setState(state)
                .setLoginTime(TimeEncoding.toProtobufTimestamp(client.getLoginTime()));

        Processor processor = client.getProcessor();
        if (processor != null) {
            clientb.setInstance(processor.getInstance());
            clientb.setProcessorName(processor.getName());
        }
        return clientb.build();
    }
}
