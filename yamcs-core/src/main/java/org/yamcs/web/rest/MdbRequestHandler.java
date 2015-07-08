package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Rest.RestDataSource;
import org.yamcs.protobuf.Rest.RestDumpRawMdbRequest;
import org.yamcs.protobuf.Rest.RestDumpRawMdbResponse;
import org.yamcs.protobuf.Rest.RestGetParameterInfoRequest;
import org.yamcs.protobuf.Rest.RestGetParameterInfoResponse;
import org.yamcs.protobuf.Rest.RestListAvailableParametersRequest;
import org.yamcs.protobuf.Rest.RestListAvailableParametersResponse;
import org.yamcs.protobuf.Rest.RestNameDescription;
import org.yamcs.protobuf.Rest.RestParameter;
import org.yamcs.protobuf.Rest.RestParameterInfo;
import org.yamcs.protobuf.Rest.RestParameterType;
import org.yamcs.protobuf.Rest.RestUnitType;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Handles incoming requests related to the Mission Database (offset /mdb).
 */
public class MdbRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(MdbRequestHandler.class);

    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri, AuthenticationToken authToken) throws RestException {
        
        //because it is difficult to send GET requests with body from some clients (including jquery), we allow POST requests here.
        //       if (req.getMethod() != HttpMethod.GET)
        //            throw new MethodNotAllowedException(req.getMethod());
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
        if ("parameters".equals(qsDecoder.path())) {
            RestListAvailableParametersRequest request = readMessage(req, SchemaRest.RestListAvailableParametersRequest.MERGE).build();
            RestListAvailableParametersResponse responseMsg = listAvailableParameters(request, yamcsInstance);
            writeMessage(ctx, req, qsDecoder, responseMsg, SchemaRest.RestListAvailableParametersResponse.WRITE);
        } else if ("dump".equals(qsDecoder.path())) {
            RestDumpRawMdbRequest request = readMessage(req, SchemaRest.RestDumpRawMdbRequest.MERGE).build();
            RestDumpRawMdbResponse responseMsg = dumpRawMdb(request, yamcsInstance);
            writeMessage(ctx, req, qsDecoder, responseMsg, SchemaRest.RestDumpRawMdbResponse.WRITE);
        } else if ("parameterInfo".equals(qsDecoder.path())) {
            if (qsDecoder.parameters().containsKey("name")) { //one parameter specified in the URL, we return one RestParameterInfo
                String name = qsDecoder.parameters().get("name").get(0);
                NamedObjectId.Builder noib = NamedObjectId.newBuilder();
                noib.setName(name);
                if (qsDecoder.parameters().containsKey("namespace")) {
                    noib.setNamespace(qsDecoder.parameters().get("namespace").get(0));
                }
                NamedObjectId id = noib.build();
                XtceDb xtceDb = loadMdb(yamcsInstance);
                Parameter p = xtceDb.getParameter(id);
                if(p==null) {
                    throw new BadRequestException("Invalid parameter name specified "+id);
                }
                 if(!Privilege.getInstance().hasPrivilege(authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                     log.warn("Parameter Info not authorized for token {}, throwing BadRequestException", authToken);
                     throw new BadRequestException("Invalid parameter name specified "+id);
                }
                RestParameterInfo pinfo = getParameterInfo(id, p);
                writeMessage(ctx, req, qsDecoder, pinfo, SchemaRest.RestParameterInfo.WRITE);
            } else { //possible multiple parameter requested, we return  RestGetParameterInfoResponse
                RestGetParameterInfoRequest request = readMessage(req, SchemaRest.RestGetParameterInfoRequest.MERGE).build();
                RestGetParameterInfoResponse responseMsg = getParameterInfo(request, yamcsInstance, authToken);
                writeMessage(ctx, req, qsDecoder, responseMsg, SchemaRest.RestGetParameterInfoResponse.WRITE);
            }
            
        } else {
            log.debug("No match for '" + qsDecoder.path() + "'");
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
        }
    }

    /**
     * Sends the parameters for the requested yamcs instance. If no namespaces are specified, send qualified names.
     */
    private RestListAvailableParametersResponse listAvailableParameters(RestListAvailableParametersRequest request, String yamcsInstance) throws RestException {
        XtceDb mdb = loadMdb(yamcsInstance);
        RestListAvailableParametersResponse.Builder responseb = RestListAvailableParametersResponse.newBuilder();
        if (request.getNamespacesCount() == 0) {
            for(Parameter p : mdb.getParameters()) {
                responseb.addParameters(toRestParameter(p.getQualifiedName(), p));
            }
        } else {
            for (Parameter p : mdb.getParameters()) {
                for (String namespace : request.getNamespacesList()) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        responseb.addParameters(toRestParameter(namespace, alias, p));
                    }
                }
            }
        }
        return responseb.build();
    }

    private static RestParameter.Builder toRestParameter(String namespace, String name, Parameter parameter) {
        RestParameter.Builder builder = toRestParameter(name, parameter);
        builder.getIdBuilder().setNamespace(namespace);
        return builder;
    }

    private static RestParameter.Builder toRestParameter(String name, Parameter parameter) {
        if(parameter.getDataSource() == null)
        {
            log.warn("Datasource for parameter " + name + " is null, setting TELEMETERED by default");
            parameter.setDataSource(DataSource.TELEMETERED);
        }
        RestDataSource ds = RestDataSource.valueOf(parameter.getDataSource().name()); // I know, i know
        return RestParameter.newBuilder().setDataSource(ds).setId(NamedObjectId.newBuilder().setName(name));
    }

    private RestDumpRawMdbResponse dumpRawMdb(RestDumpRawMdbRequest request, String yamcsInstance) throws RestException {
        RestDumpRawMdbResponse.Builder responseb = RestDumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb xtceDb = loadMdb(yamcsInstance);
        ByteString.Output bout = ByteString.newOutput();
        try (ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(xtceDb);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not serialize MDB", e);
        }
        responseb.setRawMdb(bout.toByteString());
        return responseb.build();
    }

    
    
    private RestGetParameterInfoResponse getParameterInfo(RestGetParameterInfoRequest request, String yamcsInstance, AuthenticationToken authToken) throws RestException {
        XtceDb xtceDb = loadMdb(yamcsInstance);
        
        RestGetParameterInfoResponse.Builder responseb = RestGetParameterInfoResponse.newBuilder();
        for(NamedObjectId id:request.getListList() ){
            Parameter p = xtceDb.getParameter(id);
            if(p==null) {
                throw new BadRequestException("Invalid parameter name specified "+id);
            }
             if(!Privilege.getInstance().hasPrivilege(authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists", p.getQualifiedName());
                continue;
            }
            responseb.addPinfo(getParameterInfo(id, p));
        }
        return responseb.build();
    }
    
    
    private RestParameterInfo getParameterInfo(NamedObjectId id, Parameter p) {
        RestParameterInfo.Builder rpib = RestParameterInfo.newBuilder();
        rpib.setId(id);
        rpib.setDataSource(p.getDataSource().name());
        rpib.setDescription(getNameDescription(p));
        rpib.setType(getParameterType(p.getParameterType()));
        
        return rpib.build();
    }

    private RestParameterType getParameterType(ParameterType parameterType) {
        RestParameterType.Builder rptb = RestParameterType.newBuilder();
        rptb.setDataEncoding(parameterType.getEncoding().toString());
        rptb.setEngType(parameterType.getTypeAsString());
        for(UnitType ut: parameterType.getUnitSet()) {
            rptb.addUnitSet(getUnitType(ut));
        }
        return rptb.build();
    }

    private RestUnitType getUnitType(UnitType ut) {
        return RestUnitType.newBuilder().setUnit(ut.getUnit()).build();
    }

    private RestNameDescription getNameDescription(NameDescription nd) {
        RestNameDescription.Builder rnb =  RestNameDescription.newBuilder();
        rnb.setQualifiedName(nd.getQualifiedName());
        String s = nd.getShortDescription();
        if(s!=null) rnb.setShortDescription(s);
        s = nd.getLongDescription();
        if(s!=null)rnb.setLongDescription(s);
        Map<String, String> aliases = nd.getAliasSet().getAliases();
        for(Map.Entry<String, String> me:aliases.entrySet()) {
            rnb.addAliases(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()).build());
        }
        return rnb.build();
    }

    private XtceDb loadMdb(String yamcsInstance) throws RestException {
        try {
            return XtceDbFactory.getInstance(yamcsInstance);
        } catch(ConfigurationException e) {
            log.error("Could not get MDB for instance '" + yamcsInstance + "'", e);
            throw new InternalServerErrorException("Could not get MDB for instance '" + yamcsInstance + "'", e);
        }
    }
}
