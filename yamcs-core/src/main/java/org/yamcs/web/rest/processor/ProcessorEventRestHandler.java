package org.yamcs.web.rest.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.alarms.AlarmServer.EventId;
import org.yamcs.alarms.CouldNotAcknowledgeAlarmException;
import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.protobuf.Rest.EditAlarmRequest;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;

public class ProcessorEventRestHandler extends RestHandler {

    private final static Logger log = LoggerFactory.getLogger(ProcessorEventRestHandler.class);

    @Route(path = "/api/processors/:instance/:processor/events/:name*/alarms/:seqnum", method = "PATCH")
    public void patchEventAlarm(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        EventAlarmServer alarmServer = verifyEventAlarmServer(processor);

        EventId eventId = verifyEventId(req, req.getRouteParam("name"));
        int seqNum = req.getIntegerRouteParam("seqnum");

        String state = null;
        String comment = null;
        EditAlarmRequest request = req.bodyAsMessage(EditAlarmRequest.newBuilder()).build();
        if (request.hasState()) {
            state = request.getState();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }

        if (state == null) {
            throw new BadRequestException("No state specified");
        }

        switch (state.toLowerCase()) {
        case "acknowledged":
            try {
                // TODO permissions on AlarmServer
                String username = req.getUser().getUsername();
                alarmServer.acknowledge(eventId, seqNum, username, processor.getCurrentTime(), comment);
                completeOK(req);
            } catch (CouldNotAcknowledgeAlarmException e) {
                log.debug("Did not acknowledge alarm {}. {}", seqNum, e.getMessage());
                throw new BadRequestException(e.getMessage());
            }
            break;
        default:
            throw new BadRequestException("Unsupported state '" + state + "'");
        }
    }

    private EventId verifyEventId(RestRequest req, String pathName) throws HttpException {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException(req, "No such event id (missing namespace?)");
        }
        String source = pathName.substring(0, lastSlash).replace("yamcs/event/", "");
        String type = pathName.substring(lastSlash + 1);
        return new EventId(source, type);
    }
}
