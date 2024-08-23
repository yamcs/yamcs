package org.yamcs.http.api;

import static org.yamcs.http.api.GbpToXtceAssembler.toEnumerationAlarm;
import static org.yamcs.http.api.GbpToXtceAssembler.toEnumerationContextAlarm;
import static org.yamcs.http.api.GbpToXtceAssembler.toNumericAlarm;
import static org.yamcs.http.api.GbpToXtceAssembler.toNumericContextAlarm;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.MdbPageBuilder.MdbPage;
import org.yamcs.http.api.MdbSearchHelpers.EntryMatch;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceAssembler;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractMdbApi;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.BatchGetParametersRequest;
import org.yamcs.protobuf.Mdb.BatchGetParametersResponse;
import org.yamcs.protobuf.Mdb.BatchGetParametersResponse.GetParameterResponse;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.CreateParameterRequest;
import org.yamcs.protobuf.Mdb.CreateParameterTypeRequest;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.ExportJavaMissionDatabaseRequest;
import org.yamcs.protobuf.Mdb.ExportXtceRequest;
import org.yamcs.protobuf.Mdb.GetAlgorithmRequest;
import org.yamcs.protobuf.Mdb.GetCommandRequest;
import org.yamcs.protobuf.Mdb.GetContainerRequest;
import org.yamcs.protobuf.Mdb.GetMissionDatabaseRequest;
import org.yamcs.protobuf.Mdb.GetParameterRequest;
import org.yamcs.protobuf.Mdb.GetParameterTypeRequest;
import org.yamcs.protobuf.Mdb.GetSpaceSystemRequest;
import org.yamcs.protobuf.Mdb.ListAlgorithmsRequest;
import org.yamcs.protobuf.Mdb.ListAlgorithmsResponse;
import org.yamcs.protobuf.Mdb.ListCommandsRequest;
import org.yamcs.protobuf.Mdb.ListCommandsResponse;
import org.yamcs.protobuf.Mdb.ListContainersRequest;
import org.yamcs.protobuf.Mdb.ListContainersResponse;
import org.yamcs.protobuf.Mdb.ListParameterTypesRequest;
import org.yamcs.protobuf.Mdb.ListParameterTypesResponse;
import org.yamcs.protobuf.Mdb.ListParametersRequest;
import org.yamcs.protobuf.Mdb.ListParametersResponse;
import org.yamcs.protobuf.Mdb.ListSpaceSystemsRequest;
import org.yamcs.protobuf.Mdb.ListSpaceSystemsResponse;
import org.yamcs.protobuf.Mdb.MissionDatabase;
import org.yamcs.protobuf.Mdb.MissionDatabaseItem;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.SpaceSystemInfo;
import org.yamcs.protobuf.Mdb.StreamMissionDatabaseRequest;
import org.yamcs.protobuf.Mdb.UsedByInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.YamcsInstance;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;

import com.google.protobuf.ByteString;

public class MdbApi extends AbstractMdbApi<Context> {

    private static final String JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    private static final String TEXT_XML = "text/xml";
    private static final Log log = new Log(MdbApi.class);

    @Override
    public void getMissionDatabase(Context ctx, GetMissionDatabaseRequest request,
            Observer<MissionDatabase> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);
        MissionDatabase converted = toMissionDatabase(instance, mdb);
        observer.complete(converted);
    }

    @Override
    public void exportJavaMissionDatabase(Context ctx, ExportJavaMissionDatabaseRequest request,
            Observer<HttpBody> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        try (ByteString.Output output = ByteString.newOutput()) {
            try (ObjectOutputStream oos = new ObjectOutputStream(output)) {
                oos.writeObject(mdb);
            } catch (IOException e) {
                throw new InternalServerErrorException("Could not serialize MDB", e);
            }

            HttpBody httpBody = HttpBody.newBuilder()
                    .setContentType(JAVA_SERIALIZED_OBJECT)
                    .setData(output.toByteString())
                    .build();

            observer.complete(httpBody);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void streamMissionDatabase(Context ctx, StreamMissionDatabaseRequest request,
            Observer<MissionDatabaseItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        if (!request.hasIncludeSpaceSystems() || request.getIncludeSpaceSystems()) {
            for (var spaceSystem : mdb.getSpaceSystems()) {
                var item = MissionDatabaseItem.newBuilder()
                        .setSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(spaceSystem, DetailLevel.FULL))
                        .build();
                observer.next(item);
            }
        }
        if (!request.hasIncludeContainers() || request.getIncludeContainers()) {
            for (var container : mdb.getSequenceContainers()) {
                var item = MissionDatabaseItem.newBuilder()
                        .setContainer(XtceToGpbAssembler.toContainerInfo(container, DetailLevel.FULL))
                        .build();
                observer.next(item);
            }
        }
        if (!request.hasIncludeParameters() || request.getIncludeParameters()) {
            for (var parameter : mdb.getParameters()) {
                var item = MissionDatabaseItem.newBuilder()
                        .setParameter(XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.FULL))
                        .build();
                observer.next(item);
            }
        }
        if (!request.hasIncludeParameterTypes() || request.getIncludeParameterTypes()) {
            for (var parameterType : mdb.getParameterTypes()) {
                var item = MissionDatabaseItem.newBuilder()
                        .setParameterType(XtceToGpbAssembler.toParameterTypeInfo(parameterType, DetailLevel.FULL))
                        .build();
                observer.next(item);
            }
        }
        if (!request.hasIncludeCommands() || request.getIncludeCommands()) {
            for (var command : mdb.getMetaCommands()) {
                var item = MissionDatabaseItem.newBuilder()
                        .setCommand(XtceToGpbAssembler.toCommandInfo(command, DetailLevel.FULL))
                        .build();
                observer.next(item);
            }
        }
        if (!request.hasIncludeAlgorithms() || request.getIncludeAlgorithms()) {
            for (var algorithm : mdb.getAlgorithms()) {
                var item = MissionDatabaseItem.newBuilder()
                        .setAlgorithm(XtceToGpbAssembler.toAlgorithmInfo(algorithm, DetailLevel.FULL))
                        .build();
                observer.next(item);
            }
        }

        observer.complete();
    }

    @Override
    public void listSpaceSystems(Context ctx, ListSpaceSystemsRequest request,
            Observer<ListSpaceSystemsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (request.hasQ()) {
            matcher = new NameDescriptionSearchMatcher(request.getQ());
        }

        List<SpaceSystem> matchedSpaceSystems = new ArrayList<>();
        for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
            if (matcher != null && !matcher.matches(spaceSystem)) {
                continue;
            }
            matchedSpaceSystems.add(spaceSystem);
        }

        Collections.sort(matchedSpaceSystems, (p1, p2) -> {
            return p1.getQualifiedName().compareTo(p2.getQualifiedName());
        });

        int totalSize = matchedSpaceSystems.size();

        String next = request.hasNext() ? request.getNext() : null;
        int pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedSpaceSystems = matchedSpaceSystems.stream().filter(p -> {
                return p.getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        } else if (pos > 0) {
            matchedSpaceSystems = matchedSpaceSystems.subList(pos, matchedSpaceSystems.size());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedSpaceSystems.size()) {
            matchedSpaceSystems = matchedSpaceSystems.subList(0, limit);
            SpaceSystem lastSpaceSystem = matchedSpaceSystems.get(limit - 1);
            continuationToken = new NamedObjectPageToken(lastSpaceSystem.getQualifiedName(), false);
        }

        ListSpaceSystemsResponse.Builder responseb = ListSpaceSystemsResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (SpaceSystem s : matchedSpaceSystems) {
            responseb.addSpaceSystems(XtceToGpbAssembler.toSpaceSystemInfo(s, DetailLevel.FULL));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void exportXtce(Context ctx, ExportXtceRequest request, Observer<HttpBody> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        var instance = InstancesApi.verifyInstance(request.getInstance());
        var mdb = MdbFactory.getInstance(instance);
        var spaceSystem = verifySpaceSystem(mdb, request.getName());
        var xtce = new XtceAssembler().toXtce(mdb, spaceSystem.getQualifiedName(), fqn -> true);
        var httpBody = HttpBody.newBuilder()
                .setContentType(TEXT_XML)
                .setFilename(spaceSystem.getName() + ".xtce.xml")
                .setData(ByteString.copyFromUtf8(xtce))
                .build();
        observer.complete(httpBody);
    }

    @Override
    public void getSpaceSystem(Context ctx, GetSpaceSystemRequest request, Observer<SpaceSystemInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(instance);
        SpaceSystem spaceSystem = verifySpaceSystem(mdb, request.getName());

        SpaceSystemInfo info = XtceToGpbAssembler.toSpaceSystemInfo(spaceSystem, DetailLevel.FULL);
        observer.complete(info);
    }

    @Override
    public void listParameters(Context ctx, ListParametersRequest request, Observer<ListParametersResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        Predicate<Parameter> hasPrivilege = p -> {
            return ctx.user.hasSystemPrivilege(SystemPrivilege.GetMissionDatabase)
                    || ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
        };

        // Establish only the parameters and space-systems that the user is authorised for
        Set<String> allSpaceSystemNames = new HashSet<>();
        List<Parameter> allParameters = new ArrayList<>();
        mdb.getParameters().stream().filter(hasPrivilege).forEach(parameter -> {
            allSpaceSystemNames.add(parameter.getSubsystemName());
            allParameters.add(parameter);
        });
        Set<SpaceSystem> allSpaceSystems = new HashSet<>();
        for (String spaceSystemName : allSpaceSystemNames) {
            SpaceSystem spaceSystem = mdb.getSpaceSystem(spaceSystemName);
            while (!spaceSystem.getName().isEmpty()) {
                allSpaceSystems.add(spaceSystem);
                spaceSystem = spaceSystem.getParent();
            }
        }

        // Determine search scope within the tree
        List<SpaceSystem> spaceSystems = new ArrayList<>();
        final List<Parameter> candidates = new ArrayList<>();
        if (request.hasSystem()) {
            // Add trailing slash, to ignore siblings with similar name
            var systemPrefix = request.getSystem().endsWith("/")
                    ? request.getSystem()
                    : request.getSystem() + "/";

            if (request.hasQ()) { // get candidates for deep search starting from the system
                allParameters.forEach(parameter -> {
                    if (parameter.getQualifiedName().startsWith(systemPrefix)) {
                        candidates.add(parameter);
                    }
                });
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (!allSpaceSystems.contains(spaceSystem)) {
                        continue;
                    }
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        spaceSystem.getParameters().stream().filter(hasPrivilege).forEach(candidates::add);
                    } else if (spaceSystem.getQualifiedName().startsWith(systemPrefix)) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            candidates.addAll(allParameters);
        }

        // Match parameters
        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;
        List<NameDescription> matches = candidates.stream()
                .filter(p -> !request.hasSource() || parameterSourceMatches((Parameter) p, request.getSource()))
                .filter(item -> parameterTypeMatches(item.getParameterType(), request.getTypeList()))
                .filter(item -> matcher == null || matcher.matches(item))
                .collect(Collectors.toList());

        // If requested, match also member paths inside parameters
        if (request.getSearchMembers() && request.hasQ()) {
            List<NameDescription> memberMatches = new ArrayList<>();
            for (Parameter parameter : candidates) {
                if (!request.hasSource() || parameterSourceMatches(parameter, request.getSource())) {
                    MdbSearchHelpers.searchEntries(parameter, request.getQ()).stream()
                            .filter(entry -> parameterTypeMatches(entry.entryType, request.getTypeList()))
                            .forEach(memberMatches::add);
                }
            }
            matches.addAll(memberMatches);
        }

        // We got the results now, only response formatting remaining
        MdbPageBuilder<NameDescription> pageBuilder = new MdbPageBuilder<>(spaceSystems, matches);
        pageBuilder.setNext(request.hasNext() ? request.getNext() : null);
        pageBuilder.setPos(request.hasPos() ? request.getPos() : 0);
        pageBuilder.setLimit(request.hasLimit() ? request.getLimit() : 100);
        MdbPage<NameDescription> page = pageBuilder.buildPage();

        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder()
                .setTotalSize(page.getTotalSize());
        for (SpaceSystem s : page.getSpaceSystems()) {
            responseb.addSpaceSystems(s.getQualifiedName());
            responseb.addSystems(XtceToGpbAssembler.toSpaceSystemInfo(s, DetailLevel.SUMMARY));
        }
        DetailLevel detail = request.getDetails() ? DetailLevel.FULL : DetailLevel.SUMMARY;
        for (NameDescription item : page.getItems()) {
            if (item instanceof Parameter) {
                responseb.addParameters(XtceToGpbAssembler.toParameterInfo((Parameter) item, detail));
            } else if (item instanceof EntryMatch) {
                EntryMatch match = (EntryMatch) item;
                Parameter parameter = match.parameter;
                ParameterInfo.Builder entryb = ParameterInfo
                        .newBuilder(XtceToGpbAssembler.toParameterInfo(parameter, detail));
                for (PathElement el : match.entryPath) {
                    if (el.getName() != null || el.getIndex() != null) {
                        entryb.addPath(el.toString());
                    }
                }
                responseb.addParameters(entryb);
            }
        }
        if (page.getContinuationToken() != null) {
            responseb.setContinuationToken(page.getContinuationToken());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getParameter(Context ctx, GetParameterRequest request, Observer<ParameterInfo> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(instance);
        ParameterWithId match = verifyParameterWithId(ctx, mdb, request.getName());

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(match, DetailLevel.FULL);

        List<ParameterEntry> parameterEntries = mdb.getParameterEntries(match.getParameter());
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

        observer.complete(pinfo);
    }

    @Override
    public void createParameterType(Context ctx, CreateParameterTypeRequest request,
            Observer<ParameterTypeInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ChangeMissionDatabase);
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        BaseDataType.Builder<?> ptypeb;
        if ("float".equals(request.getEngType())) {
            var floatb = new FloatParameterType.Builder();
            if (request.hasDefaultAlarm()) {
                var alarm = toNumericAlarm(request.getDefaultAlarm());
                floatb.setDefaultAlarm(alarm);
            }
            for (var contextAlarm : request.getContextAlarmsList()) {
                var alarm = toNumericContextAlarm(mdb, instance, contextAlarm);
                floatb.addContextAlarm(alarm);
            }
            ptypeb = floatb;
        } else if ("integer".equals(request.getEngType())) {
            var integerb = new IntegerParameterType.Builder();
            if (request.hasSigned()) {
                integerb.setSigned(request.getSigned());
            }
            if (request.hasDefaultAlarm()) {
                var alarm = toNumericAlarm(request.getDefaultAlarm());
                integerb.setDefaultAlarm(alarm);
            }
            for (var contextAlarm : request.getContextAlarmsList()) {
                var alarm = toNumericContextAlarm(mdb, instance, contextAlarm);
                integerb.addContextAlarm(alarm);
            }
            ptypeb = integerb;
        } else if ("enumeration".equals(request.getEngType())) {
            var enumb = new EnumeratedParameterType.Builder();
            for (var enumerationValue : request.getEnumerationValuesList()) {
                var valueEnumeration = new ValueEnumeration(enumerationValue.getValue(), enumerationValue.getLabel());
                if (enumerationValue.hasDescription()) {
                    valueEnumeration.setDescription(enumerationValue.getDescription());
                }
            }
            if (request.hasDefaultAlarm()) {
                var alarm = toEnumerationAlarm(request.getDefaultAlarm());
                enumb.setDefaultAlarm(alarm);
            }
            for (var contextAlarm : request.getContextAlarmsList()) {
                var alarm = toEnumerationContextAlarm(mdb, instance, contextAlarm);
                enumb.addContextAlarm(alarm);
            }
            ptypeb = enumb;
        } else if ("binary".equals(request.getEngType())) {
            var binaryb = new BinaryParameterType.Builder();
            ptypeb = binaryb;
        } else if ("boolean".equals(request.getEngType())) {
            var booleanb = new BooleanParameterType.Builder();
            if (request.hasOneStringValue()) {
                booleanb.setOneStringValue(request.getOneStringValue());
            }
            if (request.hasZeroStringValue()) {
                booleanb.setZeroStringValue(request.getZeroStringValue());
            }
            ptypeb = booleanb;
        } else if ("string".equals(request.getEngType())) {
            var stringb = new StringParameterType.Builder();
            ptypeb = stringb;
        } else {
            throw new BadRequestException(
                    "Cannot create parameters of type '" + request.getEngType() + "'");
        }

        var fqn = request.getName();
        if (!fqn.startsWith("/")) {
            throw new BadRequestException("Parameter type name is not fully qualified");
        }
        var idx = fqn.lastIndexOf('/');
        var name = fqn.substring(idx + 1);

        ptypeb.setName(name);
        ptypeb.setQualifiedName(fqn);
        request.getAliasesMap().forEach(ptypeb::addAlias);

        if (request.hasShortDescription()) {
            ptypeb.setShortDescription(request.getShortDescription());
        }
        if (request.hasLongDescription()) {
            ptypeb.setLongDescription(request.getLongDescription());
        }
        if (request.hasUnit()) {
            ptypeb.addUnit(new UnitType(request.getUnit()));
        }

        var ptype = (ParameterType) ptypeb.build();
        try {
            mdb.addParameterType(ptype, true);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not save parameter type", e);
        }
        observer.complete(XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.FULL));
    }

    @Override
    public void createParameter(Context ctx, CreateParameterRequest request, Observer<ParameterInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ChangeMissionDatabase);
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        var fqn = request.getName();
        if (!fqn.startsWith("/")) {
            throw new BadRequestException("Parameter name is not fully qualified");
        }
        var idx = fqn.lastIndexOf('/');
        var name = fqn.substring(idx + 1);

        var parameter = new Parameter(name);
        parameter.setDataSource(DataSource.valueOf(request.getDataSource().name()));
        parameter.setQualifiedName(fqn);
        request.getAliasesMap().forEach(parameter::addAlias);

        if (request.hasShortDescription()) {
            parameter.setShortDescription(request.getShortDescription());
        }
        if (request.hasLongDescription()) {
            parameter.setLongDescription(request.getLongDescription());
        }

        if (request.hasParameterType()) {
            var ptype = mdb.getParameterType(request.getParameterType());
            if (ptype == null) {
                throw new BadRequestException("Unknown parameter type '" + request.getParameterType() + "'");
            }
            parameter.setParameterType(ptype);
        }

        try {
            mdb.addParameter(parameter, true, true);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not save parameter", e);
        }

        observer.complete(XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.FULL));
    }

    @Override
    public void batchGetParameters(Context ctx, BatchGetParametersRequest request,
            Observer<BatchGetParametersResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        BatchGetParametersResponse.Builder responseb = BatchGetParametersResponse.newBuilder();
        for (NamedObjectId id : request.getIdList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }
            if (!ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                log.warn("Not providing information about parameter {} because no privileges exists",
                        p.getQualifiedName());
                continue;
            }

            GetParameterResponse.Builder response = GetParameterResponse.newBuilder();
            response.setId(id);
            response.setParameter(XtceToGpbAssembler.toParameterInfo(p, DetailLevel.SUMMARY));
            responseb.addResponse(response);
        }

        observer.complete(responseb.build());
    }

    @Override
    public void listParameterTypes(Context ctx, ListParameterTypesRequest request,
            Observer<ListParameterTypesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        List<ParameterType> ptypes = new ArrayList<>();
        if (request.hasSystem()) {
            // Add trailing slash, to ignore siblings with similar name
            var systemPrefix = request.getSystem().endsWith("/")
                    ? request.getSystem()
                    : request.getSystem() + "/";

            if (request.hasQ()) { // get candidates for deep search starting from the system
                for (ParameterType ptype : mdb.getParameterTypes()) {
                    if (ptype.getQualifiedName().startsWith(systemPrefix)) {
                        ptypes.add(ptype);
                    }
                }
            } else { // get direct children of the system
                List<SpaceSystem> filteredSpaceSystems = mdb.getSpaceSystems().stream()
                        .filter(spaceSystem -> spaceSystem.getParameterTypeCount(true) > 0)
                        .collect(Collectors.toList());
                for (SpaceSystem spaceSystem : filteredSpaceSystems) {
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        ptypes.addAll(spaceSystem.getParameterTypes());
                    } else if (spaceSystem.getQualifiedName().startsWith(systemPrefix)) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            ptypes = new ArrayList<>(mdb.getParameterTypes());
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        ptypes = ptypes.stream().filter(c -> {
            if (matcher != null && !matcher.matches((NameDescription) c)) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        @SuppressWarnings("unchecked")
        var nameDescriptionItems = (List<NameDescription>) (Object) ptypes;
        var pageBuilder = new MdbPageBuilder<>(spaceSystems, nameDescriptionItems);
        pageBuilder.setNext(request.hasNext() ? request.getNext() : null);
        pageBuilder.setPos(request.hasPos() ? request.getPos() : 0);
        pageBuilder.setLimit(request.hasLimit() ? request.getLimit() : 100);
        var page = pageBuilder.buildPage();

        var responseb = ListParameterTypesResponse.newBuilder()
                .setTotalSize(page.getTotalSize());
        for (SpaceSystem s : page.getSpaceSystems()) {
            responseb.addSpaceSystems(s.getQualifiedName());
            responseb.addSystems(XtceToGpbAssembler.toSpaceSystemInfo(s, DetailLevel.SUMMARY));
        }
        for (NameDescription c : page.getItems()) {
            var ptype = (ParameterType) c;
            responseb.addParameterTypes(XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.SUMMARY));
        }
        if (page.getContinuationToken() != null) {
            responseb.setContinuationToken(page.getContinuationToken());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getParameterType(Context ctx, GetParameterTypeRequest request, Observer<ParameterTypeInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(instance);
        ParameterType ptype = verifyParameterType(mdb, request.getName());

        var pinfo = XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.FULL);

        // Add extra information about which parameters are using this type
        // This is not efficient, but we don't expect this to get called often.
        var pinfob = ParameterTypeInfo.newBuilder(pinfo);
        mdb.getParameters().stream()
                .filter(parameter -> parameter.getParameterType() == ptype)
                .sorted((a, b) -> a.getQualifiedName().compareTo(b.getQualifiedName()))
                .forEach(parameter -> {
                    pinfob.addUsedBy(XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.LINK));
                });
        pinfo = pinfob.build();

        observer.complete(pinfo);
    }

    @Override
    public void listContainers(Context ctx, ListContainersRequest request,
            Observer<ListContainersResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        List<SequenceContainer> containers = new ArrayList<>();
        if (request.hasSystem()) {
            // Add trailing slash, to ignore siblings with similar name
            var systemPrefix = request.getSystem().endsWith("/")
                    ? request.getSystem()
                    : request.getSystem() + "/";

            if (request.hasQ()) { // get candidates for deep search starting from the system
                for (SequenceContainer container : mdb.getSequenceContainers()) {
                    if (container.getQualifiedName().startsWith(systemPrefix)) {
                        containers.add(container);
                    }
                }
            } else { // get direct children of the system
                List<SpaceSystem> filteredSpaceSystems = mdb.getSpaceSystems().stream()
                        .filter(spaceSystem -> spaceSystem.getSequenceContainerCount(true) > 0)
                        .collect(Collectors.toList());
                for (SpaceSystem spaceSystem : filteredSpaceSystems) {
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        containers.addAll(spaceSystem.getSequenceContainers());
                    } else if (spaceSystem.getQualifiedName().startsWith(systemPrefix)) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            containers = new ArrayList<>(mdb.getSequenceContainers());
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        containers = containers.stream().filter(c -> {
            if (matcher != null && !matcher.matches(c)) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        MdbPageBuilder<SequenceContainer> pageBuilder = new MdbPageBuilder<>(spaceSystems, containers);
        pageBuilder.setNext(request.hasNext() ? request.getNext() : null);
        pageBuilder.setPos(request.hasPos() ? request.getPos() : 0);
        pageBuilder.setLimit(request.hasLimit() ? request.getLimit() : 100);
        MdbPage<SequenceContainer> page = pageBuilder.buildPage();

        ListContainersResponse.Builder responseb = ListContainersResponse.newBuilder()
                .setTotalSize(page.getTotalSize());
        for (SpaceSystem s : page.getSpaceSystems()) {
            responseb.addSpaceSystems(s.getQualifiedName());
            responseb.addSystems(XtceToGpbAssembler.toSpaceSystemInfo(s, DetailLevel.SUMMARY));
        }
        for (SequenceContainer c : page.getItems()) {
            responseb.addContainers(XtceToGpbAssembler.toContainerInfo(c, DetailLevel.SUMMARY));
        }
        if (page.getContinuationToken() != null) {
            responseb.setContinuationToken(page.getContinuationToken());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getContainer(Context ctx, GetContainerRequest request, Observer<ContainerInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.GetMissionDatabase);

        String instance = InstancesApi.verifyInstance(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(instance);
        SequenceContainer c = verifyContainer(mdb, request.getName());

        ContainerInfo cinfo = XtceToGpbAssembler.toContainerInfo(c, DetailLevel.FULL);
        List<ContainerEntry> containerEntries = mdb.getContainerEntries(c);
        if (containerEntries != null) {
            ContainerInfo.Builder cinfob = ContainerInfo.newBuilder(cinfo);
            Set<SequenceContainer> usingContainers = new HashSet<>();
            for (ContainerEntry entry : containerEntries) {
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
            cinfob.setUsedBy(usedByb);
            cinfo = cinfob.build();
        }

        observer.complete(cinfo);
    }

    @Override
    public void listCommands(Context ctx, ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        Predicate<MetaCommand> hasPrivilege = c -> {
            return ctx.user.hasSystemPrivilege(SystemPrivilege.GetMissionDatabase)
                    || ctx.user.hasObjectPrivilege(ObjectPrivilegeType.Command, c.getQualifiedName());
        };

        // Establish only the commands and space-systems that the user is authorised for
        Set<String> allSpaceSystemNames = new HashSet<>();
        List<MetaCommand> allCommands = new ArrayList<>();
        mdb.getMetaCommands().stream().filter(hasPrivilege).forEach(command -> {
            allSpaceSystemNames.add(command.getSubsystemName());
            allCommands.add(command);
        });
        Set<SpaceSystem> allSpaceSystems = new HashSet<>();
        for (String spaceSystemName : allSpaceSystemNames) {
            SpaceSystem spaceSystem = mdb.getSpaceSystem(spaceSystemName);
            while (!spaceSystem.getName().isEmpty()) {
                allSpaceSystems.add(spaceSystem);
                spaceSystem = spaceSystem.getParent();
            }
        }

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        final List<MetaCommand> candidates = new ArrayList<>();
        if (request.hasSystem()) {
            // Add trailing slash, to ignore siblings with similar name
            var systemPrefix = request.getSystem().endsWith("/")
                    ? request.getSystem()
                    : request.getSystem() + "/";

            if (request.hasQ()) { // get candidates for deep search starting from the system
                allCommands.forEach(command -> {
                    if (command.getQualifiedName().startsWith(systemPrefix)) {
                        candidates.add(command);
                    }
                });
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (!allSpaceSystems.contains(spaceSystem)) {
                        continue;
                    }
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        spaceSystem.getMetaCommands().stream().filter(hasPrivilege).forEach(candidates::add);
                    } else if (spaceSystem.getQualifiedName().startsWith(systemPrefix)) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            candidates.addAll(allCommands);
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        List<MetaCommand> commands = candidates.stream().filter(c -> {
            if (matcher != null && !matcher.matches(c)) {
                return false;
            }
            if (c.isAbstract() && request.getNoAbstract()) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        MdbPageBuilder<MetaCommand> pageBuilder = new MdbPageBuilder<>(spaceSystems, commands);
        pageBuilder.setNext(request.hasNext() ? request.getNext() : null);
        pageBuilder.setPos(request.hasPos() ? request.getPos() : 0);
        pageBuilder.setLimit(request.hasLimit() ? request.getLimit() : 100);
        MdbPage<MetaCommand> page = pageBuilder.buildPage();

        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder()
                .setTotalSize(page.getTotalSize());
        for (SpaceSystem s : page.getSpaceSystems()) {
            responseb.addSpaceSystems(s.getQualifiedName());
            responseb.addSystems(XtceToGpbAssembler.toSpaceSystemInfo(s, DetailLevel.SUMMARY));
        }
        DetailLevel detail = request.getDetails() ? DetailLevel.FULL : DetailLevel.SUMMARY;
        for (MetaCommand c : page.getItems()) {
            responseb.addCommands(XtceToGpbAssembler.toCommandInfo(c, detail));
        }
        if (page.getContinuationToken() != null) {
            responseb.setContinuationToken(page.getContinuationToken());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getCommand(Context ctx, GetCommandRequest request, Observer<CommandInfo> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);
        MetaCommand cmd = verifyCommand(mdb, request.getName());

        if (!ctx.user.hasSystemPrivilege(SystemPrivilege.GetMissionDatabase) &&
                !ctx.user.hasObjectPrivilege(ObjectPrivilegeType.Command, cmd.getQualifiedName())) {
            throw new ForbiddenException("Insufficient privileges");
        }

        CommandInfo cinfo = XtceToGpbAssembler.toCommandInfo(cmd, DetailLevel.FULL);
        observer.complete(cinfo);
    }

    @Override
    public void listAlgorithms(Context ctx, ListAlgorithmsRequest request,
            Observer<ListAlgorithmsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);

        Predicate<Algorithm> hasPrivilege = a -> {
            return ctx.user.hasSystemPrivilege(SystemPrivilege.GetMissionDatabase)
                    || ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadAlgorithm, a.getQualifiedName());
        };

        // Establish only the algorithms and space-systems that the user is authorised for
        Set<String> allSpaceSystemNames = new HashSet<>();
        List<Algorithm> allAlgorithms = new ArrayList<>();
        mdb.getAlgorithms().stream().filter(hasPrivilege).forEach(algorithm -> {
            allSpaceSystemNames.add(algorithm.getSubsystemName());
            allAlgorithms.add(algorithm);
        });
        Set<SpaceSystem> allSpaceSystems = new HashSet<>();
        for (String spaceSystemName : allSpaceSystemNames) {
            SpaceSystem spaceSystem = mdb.getSpaceSystem(spaceSystemName);
            while (!spaceSystem.getName().isEmpty()) {
                allSpaceSystems.add(spaceSystem);
                spaceSystem = spaceSystem.getParent();
            }
        }

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        final List<Algorithm> candidates = new ArrayList<>();
        if (request.hasSystem()) {
            // Add trailing slash, to ignore siblings with similar name
            var systemPrefix = request.getSystem().endsWith("/")
                    ? request.getSystem()
                    : request.getSystem() + "/";

            if (request.hasQ()) { // get candidates for deep search starting from the system
                allAlgorithms.forEach(algorithm -> {
                    if (algorithm.getQualifiedName().startsWith(systemPrefix)) {
                        candidates.add(algorithm);
                    }
                });
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (!allSpaceSystems.contains(spaceSystem)) {
                        continue;
                    }
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        spaceSystem.getAlgorithms().stream().filter(hasPrivilege).forEach(candidates::add);
                    } else if (spaceSystem.getQualifiedName().startsWith(systemPrefix)) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            candidates.addAll(allAlgorithms);
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        List<Algorithm> algorithms = candidates.stream().filter(a -> {
            if (matcher != null && !matcher.matches(a)) {
                return false;
            }
            if (request.hasScope()) {
                Scope requestScope = Scope.valueOf(request.getScope().name());
                if (requestScope != a.getScope()) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        MdbPageBuilder<Algorithm> pageBuilder = new MdbPageBuilder<>(spaceSystems, algorithms);
        pageBuilder.setNext(request.hasNext() ? request.getNext() : null);
        pageBuilder.setPos(request.hasPos() ? request.getPos() : 0);
        pageBuilder.setLimit(request.hasLimit() ? request.getLimit() : 100);
        MdbPage<Algorithm> page = pageBuilder.buildPage();

        ListAlgorithmsResponse.Builder responseb = ListAlgorithmsResponse.newBuilder()
                .setTotalSize(page.getTotalSize());
        for (SpaceSystem s : page.getSpaceSystems()) {
            responseb.addSpaceSystems(s.getQualifiedName());
            responseb.addSystems(XtceToGpbAssembler.toSpaceSystemInfo(s, DetailLevel.SUMMARY));
        }
        for (Algorithm a : page.getItems()) {
            responseb.addAlgorithms(XtceToGpbAssembler.toAlgorithmInfo(a, DetailLevel.SUMMARY));
        }
        if (page.getContinuationToken() != null) {
            responseb.setContinuationToken(page.getContinuationToken());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getAlgorithm(Context ctx, GetAlgorithmRequest request, Observer<AlgorithmInfo> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);
        Algorithm algo = verifyAlgorithm(mdb, request.getName());

        if (!ctx.user.hasSystemPrivilege(SystemPrivilege.GetMissionDatabase) &&
                !ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadAlgorithm, algo.getQualifiedName())) {
            throw new ForbiddenException("Insufficient privileges");
        }

        AlgorithmInfo cinfo = XtceToGpbAssembler.toAlgorithmInfo(algo, DetailLevel.FULL);
        observer.complete(cinfo);
    }

    private boolean parameterTypeMatches(ParameterType ptype, List<String> types) {
        if (types.isEmpty()) {
            return true;
        }
        if (ptype instanceof ArrayParameterType) {
            return types.contains("array");
        }
        return ptype != null && types.contains(ptype.getTypeAsString());
    }

    private boolean parameterSourceMatches(Parameter p, DataSourceType source) {
        DataSource xtceSource = p.getDataSource();
        return xtceSource != null && xtceSource.toString().equals(source.toString());
    }

    private static SpaceSystem verifySpaceSystem(Mdb mdb, String pathName) {
        String namespace;
        String name;
        int lastSlash = pathName.lastIndexOf('/');
        if ("/".equals(pathName)) {
            namespace = "";
            name = "";
        } else if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            namespace = "";
            name = pathName;
        } else {
            namespace = pathName.substring(0, lastSlash);
            name = pathName.substring(lastSlash + 1);
        }

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        SpaceSystem spaceSystem = mdb.getSpaceSystem(id);
        if (spaceSystem != null) {
            return spaceSystem;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        spaceSystem = mdb.getSpaceSystem(id);
        if (spaceSystem != null) {
            return spaceSystem;
        }

        throw new NotFoundException("No such space system");
    }

    static Algorithm verifyAlgorithm(Mdb mdb, String pathName) {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such algorithm (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        Algorithm algorithm = mdb.getAlgorithm(id);
        if (algorithm != null) {
            return algorithm;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        algorithm = mdb.getAlgorithm(id);
        if (algorithm != null) {
            return algorithm;
        }

        throw new NotFoundException("No such algorithm");
    }

    static ParameterType verifyParameterType(Mdb mdb, String pathName) {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such parameter type (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        ParameterType type = mdb.getParameterType(id);
        if (type != null) {
            return type;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        type = mdb.getParameterType(id);
        if (type != null) {
            return type;
        }

        throw new NotFoundException("No such parameter type");
    }

    static SequenceContainer verifyContainer(Mdb mdb, String pathName) {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such container (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        SequenceContainer container = mdb.getSequenceContainer(id);
        if (container != null) {
            return container;
        }

        // Maybe some non-xtce namespace like MDB:OPS Name
        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        container = mdb.getSequenceContainer(id);
        if (container != null) {
            return container;
        }

        throw new NotFoundException("No such container");
    }

    static MetaCommand verifyCommand(Mdb mdb, String pathName) {
        int lastSlash = pathName.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == pathName.length() - 1) {
            throw new NotFoundException("No such command (missing namespace?)");
        }

        String namespace = pathName.substring(0, lastSlash);
        String name = pathName.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        NamedObjectId id = NamedObjectId.newBuilder().setNamespace("/" + namespace).setName(name).build();
        MetaCommand cmd = mdb.getMetaCommand(id);
        if (cmd == null) {
            // Maybe some non-xtce namespace like MDB:OPS Name
            id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
            cmd = mdb.getMetaCommand(id);
        }

        if (cmd == null) {
            throw new NotFoundException("No such command");
        } else {
            return cmd;
        }
    }

    static NamedObjectId verifyParameterId(Context ctx, Mdb mdb, String pathName) {
        return verifyParameterWithId(ctx, mdb, pathName).getId();
    }

    public static Parameter verifyParameter(Context ctx, Mdb mdb, String pathName) {
        return verifyParameterWithId(ctx, mdb, pathName).getParameter();
    }

    static ParameterWithId verifyParameterWithId(Context ctx, Mdb mdb, String pathName) {
        int aggSep = AggregateUtil.findSeparator(pathName);

        PathElement[] aggPath = null;
        String nwa = pathName; // name without the aggregate part
        if (aggSep >= 0) {
            nwa = pathName.substring(0, aggSep);
            try {
                aggPath = AggregateUtil.parseReference(pathName.substring(aggSep));
            } catch (IllegalArgumentException e) {
                throw new NotFoundException("Invalid array/aggregate path in name " + pathName);
            }
        }

        //
        // }
        int lastSlash = nwa.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == nwa.length() - 1) {
            throw new NotFoundException("No such parameter (missing namespace?)");
        }

        String _namespace = nwa.substring(0, lastSlash);
        String name = nwa.substring(lastSlash + 1);

        // First try with a prefixed slash (should be the common case)
        String namespace = "/" + _namespace;
        Parameter p = mdb.getParameter(namespace, name);
        if (p == null) {
            namespace = _namespace;
            // Maybe some non-xtce namespace like MDB:OPS Name
            p = mdb.getParameter(namespace, name);
        }

        if (p != null && !ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
            throw new ForbiddenException("Insufficient privileges to access parameter " + p.getQualifiedName());
        }
        if (p == null) {
            throw new NotFoundException("No parameter named " + pathName);
        }

        if (aggPath != null) {
            if (!AggregateUtil.verifyPath(p.getParameterType(), aggPath)) {
                throw new NotFoundException("Nonexistent array/aggregate path in name " + pathName);
            }
            name += AggregateUtil.toString(aggPath);
        }

        NamedObjectId id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
        return new ParameterWithId(p, id, aggPath);
    }

    public static MissionDatabase toMissionDatabase(String instanceName, Mdb mdb) {
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
            b.addSpaceSystems(XtceToGpbAssembler.toSpaceSystemInfo(sub, DetailLevel.FULL));
            b.addSpaceSystem(XtceToGpbAssembler.toSpaceSystemInfo(sub, DetailLevel.FULL));
        }
        return b.build();
    }
}
