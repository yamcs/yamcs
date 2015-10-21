package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Parameters.AlarmInfo;
import org.yamcs.protobuf.Parameters.AlarmLevel;
import org.yamcs.protobuf.Parameters.AlarmRange;
import org.yamcs.protobuf.Parameters.DataSource;
import org.yamcs.protobuf.Parameters.GetParameterInfoRequest;
import org.yamcs.protobuf.Parameters.GetParameterInfoResponse;
import org.yamcs.protobuf.Parameters.ListAvailableParametersRequest;
import org.yamcs.protobuf.Parameters.ListAvailableParametersResponse;
import org.yamcs.protobuf.Parameters.NameDescriptionType;
import org.yamcs.protobuf.Parameters.ParameterInfo;
import org.yamcs.protobuf.Parameters.ParameterSummary;
import org.yamcs.protobuf.Parameters.ParameterTypeInfo;
import org.yamcs.protobuf.Parameters.UnitInfo;
import org.yamcs.protobuf.SchemaParameters;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.DumpRawMdbResponse;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.xtce.AlarmRanges;
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
                ParameterInfo pinfo = getParameterInfo(id, p);
                return new RestResponse(req, pinfo, SchemaParameters.ParameterInfo.WRITE);
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
        ListAvailableParametersRequest request = req.bodyAsMessage(SchemaParameters.ListAvailableParametersRequest.MERGE).build();
        XtceDb mdb = loadMdb(req.yamcsInstance);
        ListAvailableParametersResponse.Builder responseb = ListAvailableParametersResponse.newBuilder();
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
        
        return new RestResponse(req, responseb.build(), SchemaParameters.ListAvailableParametersResponse.WRITE);
    }

    private static ParameterSummary.Builder toRestParameter(String namespace, String name, Parameter parameter) {
        ParameterSummary.Builder builder = toRestParameter(name, parameter);
        builder.getIdBuilder().setNamespace(namespace);
        return builder;
    }

    private static ParameterSummary.Builder toRestParameter(String name, Parameter parameter) {
        org.yamcs.xtce.DataSource xtceDs = parameter.getDataSource();
        if (xtceDs == null) {
            log.warn("Datasource for parameter " + name + " is null, setting TELEMETERED by default");
            xtceDs = org.yamcs.xtce.DataSource.TELEMETERED;
        }
        DataSource ds = DataSource.valueOf(xtceDs.name()); // I know, i know
        return ParameterSummary.newBuilder().setDataSource(ds).setId(NamedObjectId.newBuilder().setName(name));
    }

    private RestResponse dumpRawMdb(RestRequest req) throws RestException {
        DumpRawMdbResponse.Builder responseb = DumpRawMdbResponse.newBuilder();

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
        return new RestResponse(req, responseb.build(), SchemaYamcs.DumpRawMdbResponse.WRITE);
    }
    
    private RestResponse getParameterInfo(RestRequest req) throws RestException {
        XtceDb xtceDb = loadMdb(req.yamcsInstance);
        
        GetParameterInfoRequest request = req.bodyAsMessage(SchemaParameters.GetParameterInfoRequest.MERGE).build();
        GetParameterInfoResponse.Builder responseb = GetParameterInfoResponse.newBuilder();
        for(NamedObjectId id:request.getListList()) {
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
        
        return new RestResponse(req, responseb.build(), SchemaParameters.GetParameterInfoResponse.WRITE);
    }
    
    private ParameterInfo getParameterInfo(NamedObjectId id, Parameter p) {
        ParameterInfo.Builder rpib = ParameterInfo.newBuilder();
        rpib.setId(id);
        org.yamcs.xtce.DataSource ds = p.getDataSource();
        if(ds!=null) {
            rpib.setDataSource(ds.name());
        }
        rpib.setDescription(getNameDescription(p));
        rpib.setType(getParameterType(p.getParameterType()));
        
        return rpib.build();
    }

    private ParameterTypeInfo getParameterType(ParameterType parameterType) {
        ParameterTypeInfo.Builder rptb = ParameterTypeInfo.newBuilder();
        rptb.setDataEncoding(parameterType.getEncoding().toString());
        rptb.setEngType(parameterType.getTypeAsString());
        for(UnitType ut: parameterType.getUnitSet()) {
            rptb.addUnitSet(getUnitInfo(ut));
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

    private UnitInfo getUnitInfo(UnitType ut) {
        return UnitInfo.newBuilder().setUnit(ut.getUnit()).build();
    }

    private NameDescriptionType getNameDescription(NameDescription nd) {
        NameDescriptionType.Builder rnb =  NameDescriptionType.newBuilder();
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
    
    private AlarmInfo getAlarmInfo(NumericAlarm numericAlarm) {
        AlarmInfo.Builder alarmInfob = AlarmInfo.newBuilder();
        alarmInfob.setMinViolations(numericAlarm.getMinViolations());
        AlarmRanges staticRanges = numericAlarm.getStaticAlarmRanges();
        if (staticRanges.getWatchRange() != null) {
            AlarmRange watchRange = getAlarmRange(AlarmLevel.watch, staticRanges.getWatchRange());
            alarmInfob.addStaticAlarmRanges(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            AlarmRange warningRange = getAlarmRange(AlarmLevel.warning, staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRanges(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            AlarmRange distressRange = getAlarmRange(AlarmLevel.distress, staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRanges(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            AlarmRange criticalRange = getAlarmRange(AlarmLevel.critical, staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRanges(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            AlarmRange severeRange = getAlarmRange(AlarmLevel.severe, staticRanges.getSevereRange());
            alarmInfob.addStaticAlarmRanges(severeRange);
        }
            
        return alarmInfob.build();
    }
    
    private AlarmRange getAlarmRange(AlarmLevel level, FloatRange alarmRange) {
        AlarmRange.Builder resultb = AlarmRange.newBuilder();
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
