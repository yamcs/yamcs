package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.DumpRawMdbResponse;
import org.yamcs.xtce.XtceDb;

import com.google.protobuf.ByteString;

/**
 * Handles incoming requests related to the Mission Database
 * <p>
 * /api/:instance/mdb
 */
public class MdbRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "mdb";
    }

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        //because it is difficult to send GET requests with body from some clients (including jquery), we allow POST requests here.
        //       req.assertGET();
        switch (req.getPathSegment(pathOffset)) {
        case "dump":
            return dumpRawMdb(req);
            
        default:
            throw new NotFoundException(req);
        }
    }

    private RestResponse dumpRawMdb(RestRequest req) throws RestException {
        DumpRawMdbResponse.Builder responseb = DumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb xtceDb = loadMdb(req.getYamcsInstance());
        ByteString.Output bout = ByteString.newOutput();
        try (ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(xtceDb);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not serialize MDB", e);
        }
        responseb.setRawMdb(bout.toByteString());
        return new RestResponse(req, responseb.build(), SchemaYamcs.DumpRawMdbResponse.WRITE);
    }
}
