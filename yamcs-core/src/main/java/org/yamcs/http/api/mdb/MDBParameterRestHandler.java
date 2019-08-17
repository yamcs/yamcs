package org.yamcs.http.api.mdb;

import static org.yamcs.http.api.mdb.GbpToXtceAssembler.toCalibrator;
import static org.yamcs.http.api.mdb.GbpToXtceAssembler.toContextCalibratorList;
import static org.yamcs.http.api.mdb.GbpToXtceAssembler.toEnumerationAlarm;
import static org.yamcs.http.api.mdb.GbpToXtceAssembler.toEnumerationContextAlarm;
import static org.yamcs.http.api.mdb.GbpToXtceAssembler.toNumericAlarm;
import static org.yamcs.http.api.mdb.GbpToXtceAssembler.toNumericContextAlarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.yamcs.Processor;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.protobuf.Mdb.BatchGetParametersRequest;
import org.yamcs.protobuf.Mdb.BatchGetParametersResponse;
import org.yamcs.protobuf.Mdb.BatchGetParametersResponse.GetParameterResponse;
import org.yamcs.protobuf.Mdb.ChangeParameterRequest;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.GetParameterRequest;
import org.yamcs.protobuf.Mdb.ListParametersRequest;
import org.yamcs.protobuf.Mdb.ListParametersResponse;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.UsedByInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessorData;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles incoming requests related to parameter info from the MDB
 */
public class MDBParameterRestHandler extends AbstractMdbHandler {

    @Override
    void getParameter(RestRequest req, GetParameterRequest request) throws HttpException {
        String instance = verifyInstance(req, request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = verifyParameter(req, mdb, request.getName());

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(p, DetailLevel.FULL);
        List<ParameterEntry> parameterEntries = mdb.getParameterEntries(p);
        if (parameterEntries != null) {
            ParameterInfo.Builder pinfob = ParameterInfo.newBuilder(pinfo);
            Set<SequenceContainer> usingContainers = new HashSet<>();
            for (ParameterEntry entry : parameterEntries) {
                Container containingContainer = entry.getContainer();
                if (containingContainer instanceof SequenceContainer) {
                    usingContainers.add((SequenceContainer) containingContainer);
                }
            }

            UsedByInfo.Builder usedByb = UsedByInfo.newBuilder();
            List<SequenceContainer> unsortedContainers = new ArrayList<>(usingContainers);
            Collections.sort(unsortedContainers, (c1, c2) -> c1.getQualifiedName().compareTo(c2.getQualifiedName()));
            for (SequenceContainer seqContainer : unsortedContainers) {
                ContainerInfo usingContainer = XtceToGpbAssembler.toContainerInfo(seqContainer, DetailLevel.LINK);
                usedByb.addContainer(usingContainer);
            }
            pinfob.setUsedBy(usedByb);
            pinfo = pinfob.build();
        }

        completeOK(req, pinfo);
    }

    @Override
    void batchGetParameters(RestRequest req, BatchGetParametersRequest request) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.GetMissionDatabase);

        String instance = verifyInstance(req, request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        BatchGetParametersResponse.Builder responseb = BatchGetParametersResponse.newBuilder();
        for (NamedObjectId id : request.getIdList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }
            if (!hasObjectPrivilege(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists",
                        p.getQualifiedName());
                continue;
            }

            GetParameterResponse.Builder response = GetParameterResponse.newBuilder();
            response.setId(id);
            response.setParameter(XtceToGpbAssembler.toParameterInfo(p, DetailLevel.SUMMARY));
            responseb.addResponse(response);
        }

        completeOK(req, responseb.build());
    }

    @Override
    public void listParameters(RestRequest req, ListParametersRequest request) throws HttpException {
        String instance = verifyInstance(req, request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (request.hasQ()) {
            matcher = new NameDescriptionSearchMatcher(request.getQ());
        }

        boolean recurse = request.hasRecurse() && request.getRecurse();
        boolean details = request.hasDetails() && request.getDetails();

        List<Parameter> matchedParameters = new ArrayList<>();
        if (request.hasNamespace()) {
            String namespace = request.getNamespace();
            for (Parameter p : mdb.getParameters()) {
                if (!hasObjectPrivilege(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                    continue;
                }
                if (matcher != null && !matcher.matches(p)) {
                    continue;
                }

                String alias = p.getAlias(namespace);
                if (alias != null || (recurse && p.getQualifiedName().startsWith(namespace))) {
                    if (parameterTypeMatches(p, request.getTypeList())) {
                        matchedParameters.add(p);
                    }
                }
            }
        } else { // List all
            for (Parameter p : mdb.getParameters()) {
                if (!hasObjectPrivilege(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                    continue;
                }
                if (matcher != null && !matcher.matches(p)) {
                    continue;
                }
                if (parameterTypeMatches(p, request.getTypeList())) {
                    matchedParameters.add(p);
                }
            }
        }

        Collections.sort(matchedParameters, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        int totalSize = matchedParameters.size();

        String next = request.hasNext() ? request.getNext() : null;
        int pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedParameters = matchedParameters.stream().filter(p -> {
                return p.getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        } else if (pos > 0) {
            matchedParameters = matchedParameters.subList(pos, matchedParameters.size());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedParameters.size()) {
            matchedParameters = matchedParameters.subList(0, limit);
            Parameter lastParameter = matchedParameters.get(limit - 1);
            continuationToken = new NamedObjectPageToken(lastParameter.getQualifiedName());
        }

        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (Parameter p : matchedParameters) {
            responseb.addParameters(
                    XtceToGpbAssembler.toParameterInfo(p, details ? DetailLevel.FULL : DetailLevel.SUMMARY));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        completeOK(req, responseb.build());
    }

    private boolean parameterTypeMatches(Parameter p, List<String> types) {
        if (types.isEmpty()) {
            return true;
        }
        return p.getParameterType() != null
                && types.contains(p.getParameterType().getTypeAsString());
    }

    @Route(path = "/api/mdb/{instance}/{processor}/parameters/{name*}", method = { "PATCH", "PUT", "POST" })
    public void setParameterCalibrators(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ChangeMissionDatabase);

        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req, xtcedb, req.getRouteParam("name"));
        ChangeParameterRequest cpr = req.bodyAsMessage(ChangeParameterRequest.newBuilder()).build();
        ProcessorData pdata = processor.getProcessorData();
        ParameterType origParamType = p.getParameterType();

        switch (cpr.getAction()) {
        case RESET:
            pdata.clearParameterOverrides(p);
            break;
        case RESET_CALIBRATORS:
            pdata.clearParameterCalibratorOverrides(p);
            break;
        case SET_CALIBRATORS:
            verifyNumericParameter(p);
            if (cpr.hasDefaultCalibrator()) {
                pdata.setDefaultCalibrator(p, toCalibrator(cpr.getDefaultCalibrator()));
            }
            pdata.setContextCalibratorList(p,
                    toContextCalibratorList(xtcedb, p.getSubsystemName(), cpr.getContextCalibratorList()));
            break;
        case SET_DEFAULT_CALIBRATOR:
            verifyNumericParameter(p);
            if (cpr.hasDefaultCalibrator()) {
                pdata.setDefaultCalibrator(p, toCalibrator(cpr.getDefaultCalibrator()));
            } else {
                pdata.removeDefaultCalibrator(p);
            }
            break;
        case RESET_ALARMS:
            pdata.clearParameterAlarmOverrides(p);
            break;
        case SET_DEFAULT_ALARMS:
            if (!cpr.hasDefaultAlarm()) {
                pdata.removeDefaultAlarm(p);
            } else {
                if (origParamType instanceof NumericParameterType) {
                    pdata.setDefaultNumericAlarm(p, toNumericAlarm(cpr.getDefaultAlarm()));
                } else if (origParamType instanceof EnumeratedParameterType) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(cpr.getDefaultAlarm()));
                } else {
                    throw new BadRequestException("Can only set alarms on numeric or enumerated parameters");
                }
            }
            break;
        case SET_ALARMS:
            if (origParamType instanceof NumericParameterType) {
                if (cpr.hasDefaultAlarm()) {
                    pdata.setDefaultNumericAlarm(p, toNumericAlarm(cpr.getDefaultAlarm()));
                }
                pdata.setNumericContextAlarm(p,
                        toNumericContextAlarm(xtcedb, p.getSubsystemName(), cpr.getContextAlarmList()));
            } else if (origParamType instanceof EnumeratedParameterType) {
                if (cpr.hasDefaultAlarm()) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(cpr.getDefaultAlarm()));
                }
                pdata.setEnumerationContextAlarm(p,
                        toEnumerationContextAlarm(xtcedb, p.getSubsystemName(), cpr.getContextAlarmList()));
            } else {
                throw new BadRequestException("Can only set alarms on numeric or enumerated parameters");
            }
            break;
        default:
            throw new BadRequestException("Unknown action " + cpr.getAction());

        }
        ParameterType ptype = pdata.getParameterType(p);
        ParameterTypeInfo pinfo = XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.FULL);
        completeOK(req, pinfo);
    }

    private static void verifyNumericParameter(Parameter p) throws BadRequestException {
        ParameterType ptype = p.getParameterType();
        if (!(ptype instanceof NumericParameterType)) {
            throw new BadRequestException(
                    "Cannot set a calibrator on a non numeric parameter type (" + ptype.getTypeAsString() + ")");
        }
    }
}
