package org.yamcs.web.rest;

import java.io.ObjectOutputStream;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.yamcs.protobuf.Rest.RestDumpRawMdbRequest;
import org.yamcs.protobuf.Rest.RestDumpRawMdbResponse;
import org.yamcs.protobuf.Rest.RestListAvailableParametersRequest;
import org.yamcs.protobuf.Rest.RestListAvailableParametersResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

/**
 * Handles incoming requests related to the Mission Database (offset /mdb).
 */
public class MdbRequestHandler extends RestRequestHandler {

    @Override
    public void handleRequest(ChannelHandlerContext ctx, HttpRequest httpRequest, MessageEvent evt, String yamcsInstance, String remainingUri) throws Exception {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
        if ("parameters".equals(remainingUri)) {
            RestListAvailableParametersRequest request = readMessage(httpRequest, SchemaRest.RestListAvailableParametersRequest.MERGE).build();
            MessageLite responseMsg = listAvailableParameters(request, yamcsInstance);
            writeMessage(httpRequest, qsDecoder, evt, responseMsg, SchemaRest.RestListAvailableParametersResponse.WRITE);
        } else if ("dump".equals(remainingUri)) {
            RestDumpRawMdbRequest request = readMessage(httpRequest, SchemaRest.RestDumpRawMdbRequest.MERGE).build();
            MessageLite responseMsg = dumpRawMdb(request, yamcsInstance);
            writeMessage(httpRequest, qsDecoder, evt, responseMsg, SchemaRest.RestDumpRawMdbResponse.WRITE);
        } else {
            sendError(ctx, BAD_REQUEST);
        }
    }

    /**
     * Sends the XTCEDB for the requested yamcs instance.
     * <p>
     * Currently only sends MDB:OPS Name names.
     */
    private RestListAvailableParametersResponse listAvailableParameters(RestListAvailableParametersRequest request, String yamcsInstance) throws Exception {
        XtceDb mdb =  XtceDbFactory.getInstance(yamcsInstance);

        RestListAvailableParametersResponse.Builder responseb = RestListAvailableParametersResponse.newBuilder();

        NamedDescriptionIndex<Parameter> index = mdb.getParameterAliases();
        // TODO dump for all namespaces if not specified
        for (String namespace : request.getNamespacesList()) {
            for (String name : index.getNamesForAlias(namespace)) {
                responseb.addIds(NamedObjectId.newBuilder().setNamespace(namespace).setName(name));
            }
        }
        return responseb.build();
    }

    private RestDumpRawMdbResponse dumpRawMdb(RestDumpRawMdbRequest request, String yamcsInstance) throws Exception {
        RestDumpRawMdbResponse.Builder responseb = RestDumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb mdb = XtceDbFactory.getInstance(yamcsInstance);
        ByteString.Output bout = ByteString.newOutput();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeObject(mdb);
        oos.close();
        responseb.setRawMdb(bout.toByteString());
        return responseb.build();
    }
}
