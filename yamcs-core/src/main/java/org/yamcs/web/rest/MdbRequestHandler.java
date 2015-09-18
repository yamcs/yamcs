package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Rest.RestAlarmInfo;
import org.yamcs.protobuf.Rest.RestAlarmLevel;
import org.yamcs.protobuf.Rest.RestAlarmRange;
import org.yamcs.protobuf.Rest.RestDataSource;
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
import org.yamcs.security.Privilege;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;

/**
 * Handles incoming requests related to the Mission Database
 * <p>
 * /(instance)/api/mdb
 */
public class MdbRequestHandler implements RestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(MdbRequestHandler.class);

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        //because it is difficult to send GET requests with body from some clients (including jquery), we allow POST requests here.
        //       req.assertGET();
        switch (req.getPathSegment(pathOffset)) {
        case "parameters":
            return listAvailableParameters(req);
            
        case "dump":
            return dumpRawMdb(req);
            
        case "parameterInfo":
            if (req.getQueryParameters().containsKey("name")) { //one parameter specified in the URL, we return one RestParameterInfo
                String name = req.getQueryParameters().get("name").get(0);                
                NamedObjectId.Builder noib = NamedObjectId.newBuilder();
                noib.setName(name);
                if (req.getQueryParameters().containsKey("namespace")) {
                    noib.setNamespace(req.getQueryParameters().get("namespace").get(0));
                }
                NamedObjectId id = noib.build();
                XtceDb xtceDb = loadMdb(req.yamcsInstance);
                Parameter p = xtceDb.getParameter(id);
                if(p==null) {
                    log.warn("Invalid parameter name specified: {}", id);
                    throw new BadRequestException("Invalid parameter name specified "+id);
                }
                if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                    log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
                    throw new BadRequestException("Invalid parameter name specified "+id);
                }
                RestParameterInfo pinfo = getParameterInfo(id, p);
                return new RestResponse(req, pinfo, SchemaRest.RestParameterInfo.WRITE);
            } else { //possible multiple parameter requested, we return  RestGetParameterInfoResponse
                return getParameterInfo(req);
            }
            
        default:
            throw new NotFoundException(req);
        }
    }

    /**
     * Sends the parameters for the requested yamcs instance. If no namespaces are specified, send qualified names.
     */
    private RestResponse listAvailableParameters(RestRequest req) throws RestException {
        RestListAvailableParametersRequest request = req.bodyAsMessage(SchemaRest.RestListAvailableParametersRequest.MERGE).build();
        XtceDb mdb = loadMdb(req.yamcsInstance);
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
        
        return new RestResponse(req, responseb.build(), SchemaRest.RestListAvailableParametersResponse.WRITE);
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

    private RestResponse dumpRawMdb(RestRequest req) throws RestException {
        RestDumpRawMdbResponse.Builder responseb = RestDumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb xtceDb = loadMdb(req.yamcsInstance);
        ByteString.Output bout = ByteString.newOutput();
        try (ObjectOutputStream oos = new ObjectOutputStream(bout)) {
            oos.writeObject(xtceDb);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not serialize MDB", e);
        }
        responseb.setRawMdb(bout.toByteString());
        return new RestResponse(req, responseb.build(), SchemaRest.RestDumpRawMdbResponse.WRITE);
    }

    
    
    private RestResponse getParameterInfo(RestRequest req) throws RestException {
        XtceDb xtceDb = loadMdb(req.yamcsInstance);
        
        RestGetParameterInfoRequest request = req.bodyAsMessage(SchemaRest.RestGetParameterInfoRequest.MERGE).build();
        RestGetParameterInfoResponse.Builder responseb = RestGetParameterInfoResponse.newBuilder();
        for(NamedObjectId id:request.getListList() ){
            Parameter p = xtceDb.getParameter(id);
            if(p==null) {
                throw new BadRequestException("Invalid parameter name specified "+id);
            }
            if(!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists", p.getQualifiedName());
                continue;
            }
            responseb.addPinfo(getParameterInfo(id, p));
        }
        
        return new RestResponse(req, responseb.build(), SchemaRest.RestGetParameterInfoResponse.WRITE);
    }
    
    
    private RestParameterInfo getParameterInfo(NamedObjectId id, Parameter p) {
        RestParameterInfo.Builder rpib = RestParameterInfo.newBuilder();
        rpib.setId(id);
        DataSource ds = p.getDataSource();
        if(ds!=null) {
            rpib.setDataSource(ds.name());
        }
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
        
        if (parameterType instanceof IntegerParameterType) {
            IntegerParameterType ipt = (IntegerParameterType) parameterType;
            if (ipt.getDefaultAlarm() != null) {
                rptb.setDefaultAlarm(getAlarmInfo(ipt.getDefaultAlarm()));
            }
        } else if (parameterType instanceof FloatParameterType) {
            FloatParameterType fpt = (FloatParameterType) parameterType;
            if (fpt.getDefaultAlarm() != null) {
                rptb.setDefaultAlarm(getAlarmInfo(fpt.getDefaultAlarm()));
            }
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
    
    private RestAlarmInfo getAlarmInfo(NumericAlarm numericAlarm) {
        RestAlarmInfo.Builder alarmInfob = RestAlarmInfo.newBuilder();
        alarmInfob.setMinViolations(numericAlarm.getMinViolations());
        AlarmRanges staticRanges = numericAlarm.getStaticAlarmRanges();
        if (staticRanges.getWatchRange() != null) {
            RestAlarmRange watchRange = getAlarmRange(RestAlarmLevel.watch, staticRanges.getWatchRange());
            alarmInfob.addStaticAlarmRanges(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            RestAlarmRange warningRange = getAlarmRange(RestAlarmLevel.warning, staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRanges(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            RestAlarmRange distressRange = getAlarmRange(RestAlarmLevel.distress, staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRanges(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            RestAlarmRange criticalRange = getAlarmRange(RestAlarmLevel.critical, staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRanges(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            RestAlarmRange severeRange = getAlarmRange(RestAlarmLevel.severe, staticRanges.getSevereRange());
            alarmInfob.addStaticAlarmRanges(severeRange);
        }
            
        return alarmInfob.build();
    }
    
    private RestAlarmRange getAlarmRange(RestAlarmLevel level, FloatRange alarmRange) {
        RestAlarmRange.Builder resultb = RestAlarmRange.newBuilder();
        resultb.setLevel(level);
        if (Double.isFinite(alarmRange.getMinInclusive()))
            resultb.setMinInclusive(alarmRange.getMinInclusive());
        if (Double.isFinite(alarmRange.getMaxInclusive()))
            resultb.setMaxInclusive(alarmRange.getMaxInclusive());
        return resultb.build();
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
