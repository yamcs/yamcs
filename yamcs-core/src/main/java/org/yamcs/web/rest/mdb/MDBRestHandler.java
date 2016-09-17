package org.yamcs.web.rest.mdb;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.YamcsToGpbAssembler;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

/**
 * Handles incoming requests related to parameters
 */
public class MDBRestHandler extends RestHandler {
    
    final static Logger log = LoggerFactory.getLogger(MDBRestHandler.class);
    
    @Route(path = "/api/mdb/:instance", method = "GET")
    public void getMissionDatabase(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        
        if (req.asksFor(MediaType.JAVA_SERIALIZED_OBJECT)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            try (ObjectOutputStream oos = new ObjectOutputStream(new ByteBufOutputStream(buf))) {
                oos.writeObject(mdb);
            } catch (IOException e) {
                throw new InternalServerErrorException("Could not serialize MDB", e);
            }
            completeOK(req, MediaType.JAVA_SERIALIZED_OBJECT, buf);
        } else {
            MissionDatabase converted = YamcsToGpbAssembler.toMissionDatabase(req, instance, mdb);
            completeOK(req, converted, SchemaYamcsManagement.MissionDatabase.WRITE);
        }
    }
}
