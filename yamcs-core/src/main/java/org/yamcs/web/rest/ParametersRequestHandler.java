package org.yamcs.web.rest;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Parameters.AlarmInfo;
import org.yamcs.protobuf.Parameters.AlarmLevel;
import org.yamcs.protobuf.Parameters.AlarmRange;
import org.yamcs.protobuf.Parameters.DataSourceType;
import org.yamcs.protobuf.Parameters.ListParametersResponse;
import org.yamcs.protobuf.Parameters.NameDescriptionInfo;
import org.yamcs.protobuf.Parameters.ParameterInfo;
import org.yamcs.protobuf.Parameters.ParameterTypeInfo;
import org.yamcs.protobuf.Parameters.UnitInfo;
import org.yamcs.protobuf.SchemaParameters;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.Type;
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

/**
 * Handles incoming requests related to realtime Parameters (get/set).
 * <p>
 * /api/:instance/parameters
 */
public class ParametersRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(ParametersRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "parameters";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        XtceDb mdb = loadMdb(req.yamcsInstance);
        if (!req.hasPathSegment(pathOffset)) {
            return listAvailableParameters(req, null, mdb);
        } else {
            // Find out if it's a parameter or not. Support any namespace here. Not just XTCE
            if (req.getPathSegmentCount() - pathOffset < 2) {
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setName(lastSegment).build();
                Parameter p = mdb.getParameter(id);
                if (p != null) { // Possibly a URL-encoded qualified name
                    return getSingleParameter(req, id, p);
                } else { // Assume it's a namespace
                    return listAvailableParameters(req, lastSegment, mdb);
                }
            } else {
                String namespace = req.slicePath(pathOffset, -1);
                String rootedNamespace = "/" + namespace;
                String lastSegment = req.slicePath(-1);
                NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(lastSegment).build();
                Parameter p = mdb.getParameter(id);
                if (p != null)
                    return getSingleParameter(req, id, p);
                
                id = NamedObjectId.newBuilder().setNamespace(rootedNamespace).setName(lastSegment).build();
                p = mdb.getParameter(id);
                if (p != null)
                    return getSingleParameter(req, id, p);
                
                // Assume it's a namespace
                return listAvailableParameters(req, namespace + "/" + lastSegment, mdb);
            }
        }
    }
    
    private RestResponse getSingleParameter(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        if (!Privilege.getInstance().hasPrivilege(req.authToken, Privilege.Type.TM_PARAMETER, p.getQualifiedName())) {
            log.warn("Parameter Info for {} not authorized for token {}, throwing BadRequestException", id, req.authToken);
            throw new BadRequestException("Invalid parameter name specified "+id);
        }
        ParameterInfo pinfo = toParameterInfo(id, p);
        return new RestResponse(req, pinfo, SchemaParameters.ParameterInfo.WRITE);
    }

    /**
     * Sends the parameters for the requested yamcs instance. If no namespace
     * is specified, assumes root namespace.
     */
    private RestResponse listAvailableParameters(RestRequest req, String namespace, XtceDb mdb) throws RestException {
        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder();
        if (namespace == null) {
            for (Parameter p : mdb.getParameters()) {
                NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
                responseb.addParameter(toParameterInfo(id, p));
            }
        } else {
            String rootedNamespace = "/" + namespace;
            Privilege privilege = Privilege.getInstance();
            for (Parameter p : mdb.getParameters()) {
                if (!privilege.hasPrivilege(req.authToken, Type.TM_PARAMETER, p.getQualifiedName()))
                    continue;
                
                String alias = p.getAlias(namespace);
                if (alias != null) {
                    NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build();
                    responseb.addParameter(toParameterInfo(id, p));
                } else {
                    // Slash is not added to the URL so it makes it a bit more difficult
                    // to test for both XTCE names and other names. So just test with slash too
                    alias = p.getAlias(rootedNamespace);
                    if (alias != null) {
                        NamedObjectId id = NamedObjectId.newBuilder().setNamespace(rootedNamespace).setName(alias).build();
                        responseb.addParameter(toParameterInfo(id, p));
                    }
                }
            }
        }
        
        // There's no such thing as a list of 'namespaces' within the MDB, therefore it
        // could happen that we arrive here but that the user intended to search for a single
        // parameter rather than a list. So... return a 404 if we didn't find any match.
        if (responseb.getParameterList() == null || responseb.getParameterList().isEmpty()) {
            throw new NotFoundException(req);
        } else {
            return new RestResponse(req, responseb.build(), SchemaParameters.ListParametersResponse.WRITE);
        }
    }
    
    static ParameterInfo toParameterInfo(NamedObjectId id, Parameter p) {
        ParameterInfo.Builder rpib = ParameterInfo.newBuilder();
        rpib.setId(id);
        DataSource xtceDs = p.getDataSource();
        if (xtceDs != null) {
            DataSourceType ds = DataSourceType.valueOf(xtceDs.name()); // I know, i know
            rpib.setDataSource(ds);
        }/* else { // TODO why do we need this here. For what reason was this introduced?
            log.warn("Datasource for parameter " + id.getName() + " is null, setting TELEMETERED by default");
            rpib.setDataSource(DataSourceType.TELEMETERED);
        }*/
        
        rpib.setDescription(toNameDescription(p));
        rpib.setType(toParameterType(p.getParameterType()));
        
        return rpib.build();
    }

    private static ParameterTypeInfo toParameterType(ParameterType parameterType) {
        ParameterTypeInfo.Builder rptb = ParameterTypeInfo.newBuilder();
        if (parameterType.getEncoding() != null) {
            rptb.setDataEncoding(parameterType.getEncoding().toString());
        }
        rptb.setEngType(parameterType.getTypeAsString());
        for(UnitType ut: parameterType.getUnitSet()) {
            rptb.addUnitSet(toUnitInfo(ut));
        }
        
        if (parameterType instanceof IntegerParameterType) {
            IntegerParameterType ipt = (IntegerParameterType) parameterType;
            if (ipt.getDefaultAlarm() != null) {
                rptb.setDefaultAlarm(toAlarmInfo(ipt.getDefaultAlarm()));
            }
        } else if (parameterType instanceof FloatParameterType) {
            FloatParameterType fpt = (FloatParameterType) parameterType;
            if (fpt.getDefaultAlarm() != null) {
                rptb.setDefaultAlarm(toAlarmInfo(fpt.getDefaultAlarm()));
            }
        }
        return rptb.build();
    }

    private static UnitInfo toUnitInfo(UnitType ut) {
        return UnitInfo.newBuilder().setUnit(ut.getUnit()).build();
    }

    private static NameDescriptionInfo toNameDescription(NameDescription nd) {
        NameDescriptionInfo.Builder rnb =  NameDescriptionInfo.newBuilder();
        rnb.setQualifiedName(nd.getQualifiedName());
        String s = nd.getShortDescription();
        if(s!=null) rnb.setShortDescription(s);
        s = nd.getLongDescription();
        if(s!=null)rnb.setLongDescription(s);
        Map<String, String> aliases = nd.getAliasSet().getAliases();
        for(Map.Entry<String, String> me:aliases.entrySet()) {
            rnb.addAliases(NamedObjectId.newBuilder().setName(me.getValue()).setNamespace(me.getKey()));
        }
        return rnb.build();
    }
    
    private static AlarmInfo toAlarmInfo(NumericAlarm numericAlarm) {
        AlarmInfo.Builder alarmInfob = AlarmInfo.newBuilder();
        alarmInfob.setMinViolations(numericAlarm.getMinViolations());
        AlarmRanges staticRanges = numericAlarm.getStaticAlarmRanges();
        if (staticRanges.getWatchRange() != null) {
            AlarmRange watchRange = toAlarmRange(AlarmLevel.WATCH, staticRanges.getWatchRange());
            alarmInfob.addStaticAlarmRanges(watchRange);
        }
        if (staticRanges.getWarningRange() != null) {
            AlarmRange warningRange = toAlarmRange(AlarmLevel.WARNING, staticRanges.getWarningRange());
            alarmInfob.addStaticAlarmRanges(warningRange);
        }
        if (staticRanges.getDistressRange() != null) {
            AlarmRange distressRange = toAlarmRange(AlarmLevel.DISTRESS, staticRanges.getDistressRange());
            alarmInfob.addStaticAlarmRanges(distressRange);
        }
        if (staticRanges.getCriticalRange() != null) {
            AlarmRange criticalRange = toAlarmRange(AlarmLevel.CRITICAL, staticRanges.getCriticalRange());
            alarmInfob.addStaticAlarmRanges(criticalRange);
        }
        if (staticRanges.getSevereRange() != null) {
            AlarmRange severeRange = toAlarmRange(AlarmLevel.SEVERE, staticRanges.getSevereRange());
            alarmInfob.addStaticAlarmRanges(severeRange);
        }
            
        return alarmInfob.build();
    }
    
    private static AlarmRange toAlarmRange(AlarmLevel level, FloatRange alarmRange) {
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
