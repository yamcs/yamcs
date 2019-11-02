package org.yamcs.http.api;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.MdbPageBuilder.MdbPage;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractMdbApi;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.BatchGetParametersRequest;
import org.yamcs.protobuf.Mdb.BatchGetParametersResponse;
import org.yamcs.protobuf.Mdb.BatchGetParametersResponse.GetParameterResponse;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.ExportJavaMissionDatabaseRequest;
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
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.Mdb.SpaceSystemInfo;
import org.yamcs.protobuf.Mdb.UsedByInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;

public class MdbApi extends AbstractMdbApi<Context> {

    private static final String JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    private static final Log log = new Log(MdbApi.class);

    @Override
    public void getMissionDatabase(Context ctx, GetMissionDatabaseRequest request,
            Observer<MissionDatabase> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);

        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        MissionDatabase converted = YamcsToGpbAssembler.toMissionDatabase(instance, mdb);
        observer.complete(converted);
    }

    @Override
    public void exportJavaMissionDatabase(Context ctx, ExportJavaMissionDatabaseRequest request,
            Observer<HttpBody> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);

        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

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
    public void listSpaceSystems(Context ctx, ListSpaceSystemsRequest request,
            Observer<ListSpaceSystemsResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);
        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

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
            responseb.addSpaceSystems(XtceToGpbAssembler.toSpaceSystemInfo(s));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getSpaceSystem(Context ctx, GetSpaceSystemRequest request, Observer<SpaceSystemInfo> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);

        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        SpaceSystem spaceSystem = RestHandler.verifySpaceSystem(mdb, request.getName());

        SpaceSystemInfo info = XtceToGpbAssembler.toSpaceSystemInfo(spaceSystem);
        observer.complete(info);
    }

    @Override
    public void listParameters(Context ctx, ListParametersRequest request,
            Observer<ListParametersResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        List<Parameter> parameters = new ArrayList<>();
        if (request.hasSystem()) {
            if (request.hasQ()) { // get candidates for deep search starting from the system
                for (Parameter parameter : mdb.getParameters()) {
                    if (parameter.getQualifiedName().startsWith(request.getSystem())) {
                        parameters.add(parameter);
                    }
                }
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        parameters.addAll(spaceSystem.getParameters());
                    } else if (spaceSystem.getQualifiedName().startsWith(request.getSystem())) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            parameters = new ArrayList<>(mdb.getParameters());
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        parameters = parameters.stream().filter(p -> {
            if (!RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.ReadParameter,
                    p.getQualifiedName())) {
                return false;
            }
            if (matcher != null && !matcher.matches(p)) {
                return false;
            }
            if (parameterTypeMatches(p, request.getTypeList())) {
                if (!request.hasSource() || parameterSourceMatches(p, request.getSource())) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());

        MdbPageBuilder<Parameter> pageBuilder = new MdbPageBuilder<>(spaceSystems, parameters);
        pageBuilder.setNext(request.hasNext() ? request.getNext() : null);
        pageBuilder.setPos(request.hasPos() ? request.getPos() : 0);
        pageBuilder.setLimit(request.hasLimit() ? request.getLimit() : 100);
        MdbPage<Parameter> page = pageBuilder.buildPage();

        ListParametersResponse.Builder responseb = ListParametersResponse.newBuilder()
                .setTotalSize(page.getTotalSize());
        for (SpaceSystem s : page.getSpaceSystems()) {
            responseb.addSpaceSystems(s.getQualifiedName());
        }
        DetailLevel detail = request.getDetails() ? DetailLevel.FULL : DetailLevel.SUMMARY;
        for (Parameter p : page.getItems()) {
            responseb.addParameters(XtceToGpbAssembler.toParameterInfo(p, detail));
        }
        if (page.getContinuationToken() != null) {
            responseb.setContinuationToken(page.getContinuationToken());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getParameter(Context ctx, GetParameterRequest request, Observer<ParameterInfo> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = RestHandler.verifyParameter(ctx.user, mdb, request.getName());

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

        observer.complete(pinfo);
    }

    @Override
    public void batchGetParameters(Context ctx, BatchGetParametersRequest request,
            Observer<BatchGetParametersResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);

        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        BatchGetParametersResponse.Builder responseb = BatchGetParametersResponse.newBuilder();
        for (NamedObjectId id : request.getIdList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }
            if (!RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
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
        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        // Should eventually be replaced in a generic mdb search operation
        NameDescriptionSearchMatcher matcher = null;
        if (request.hasQ()) {
            matcher = new NameDescriptionSearchMatcher(request.getQ());
        }

        boolean details = request.getDetails();

        List<ParameterType> matchedTypes = new ArrayList<>();
        for (ParameterType t : mdb.getParameterTypes()) {
            if (matcher != null && !matcher.matches((NameDescription) t)) {
                continue;
            }
            matchedTypes.add(t);
        }

        Collections.sort(matchedTypes, (t1, t2) -> {
            return ((NameDescription) t1).getQualifiedName().compareTo(((NameDescription) t2).getQualifiedName());
        });

        int totalSize = matchedTypes.size();

        String next = request.hasNext() ? request.getNext() : null;
        int pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            matchedTypes = matchedTypes.stream().filter(t -> {
                return ((NameDescription) t).getQualifiedName().compareTo(pageToken.name) > 0;
            }).collect(Collectors.toList());
        } else if (pos > 0) {
            matchedTypes = matchedTypes.subList(pos, matchedTypes.size());
        }

        NamedObjectPageToken continuationToken = null;
        if (limit < matchedTypes.size()) {
            matchedTypes = matchedTypes.subList(0, limit);
            ParameterType lastType = matchedTypes.get(limit - 1);
            continuationToken = new NamedObjectPageToken(((NameDescription) lastType).getQualifiedName(), false);
        }

        ListParameterTypesResponse.Builder responseb = ListParameterTypesResponse.newBuilder();
        responseb.setTotalSize(totalSize);
        for (ParameterType t : matchedTypes) {
            responseb.addTypes(
                    XtceToGpbAssembler.toParameterTypeInfo(t, details ? DetailLevel.FULL : DetailLevel.SUMMARY));
        }
        if (continuationToken != null) {
            responseb.setContinuationToken(continuationToken.encodeAsString());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getParameterType(Context ctx, GetParameterTypeRequest request,
            Observer<ParameterTypeInfo> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        ParameterType p = RestHandler.verifyParameterType(mdb, request.getName());

        ParameterTypeInfo pinfo = XtceToGpbAssembler.toParameterTypeInfo(p, DetailLevel.FULL);
        observer.complete(pinfo);
    }

    @Override
    public void listContainers(Context ctx, ListContainersRequest request,
            Observer<ListContainersResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);
        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        List<SequenceContainer> containers = new ArrayList<>();
        if (request.hasSystem()) {
            if (request.hasQ()) { // get candidates for deep search starting from the system
                for (SequenceContainer container : mdb.getSequenceContainers()) {
                    if (container.getQualifiedName().startsWith(request.getSystem())) {
                        containers.add(container);
                    }
                }
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        containers.addAll(spaceSystem.getSequenceContainers());
                    } else if (spaceSystem.getQualifiedName().startsWith(request.getSystem())) {
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
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);

        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        SequenceContainer c = RestHandler.verifyContainer(mdb, request.getName());

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
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);
        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        List<MetaCommand> commands = new ArrayList<>();
        if (request.hasSystem()) {
            if (request.hasQ()) { // get candidates for deep search starting from the system
                for (MetaCommand command : mdb.getMetaCommands()) {
                    if (command.getQualifiedName().startsWith(request.getSystem())) {
                        commands.add(command);
                    }
                }
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        commands.addAll(spaceSystem.getMetaCommands());
                    } else if (spaceSystem.getQualifiedName().startsWith(request.getSystem())) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            commands = new ArrayList<>(mdb.getMetaCommands());
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        commands = commands.stream().filter(c -> {
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
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);
        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        MetaCommand cmd = RestHandler.verifyCommand(mdb, request.getName());

        CommandInfo cinfo = XtceToGpbAssembler.toCommandInfo(cmd, DetailLevel.FULL);
        observer.complete(cinfo);
    }

    @Override
    public void listAlgorithms(Context ctx, ListAlgorithmsRequest request,
            Observer<ListAlgorithmsResponse> observer) {
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);
        String instance = RestHandler.verifyInstance(request.getInstance());
        XtceDb mdb = XtceDbFactory.getInstance(instance);

        List<SpaceSystem> spaceSystems = new ArrayList<>();
        List<Algorithm> algorithms = new ArrayList<>();
        if (request.hasSystem()) {
            if (request.hasQ()) { // get candidates for deep search starting from the system
                for (Algorithm algorithm : mdb.getAlgorithms()) {
                    if (algorithm.getQualifiedName().startsWith(request.getSystem())) {
                        algorithms.add(algorithm);
                    }
                }
            } else { // get direct children of the system
                for (SpaceSystem spaceSystem : mdb.getSpaceSystems()) {
                    if (spaceSystem.getQualifiedName().equals(request.getSystem())) {
                        algorithms.addAll(spaceSystem.getAlgorithms());
                    } else if (spaceSystem.getQualifiedName().startsWith(request.getSystem())) {
                        if (spaceSystem.getQualifiedName().indexOf('/', request.getSystem().length() + 1) == -1) {
                            spaceSystems.add(spaceSystem);
                        }
                    }
                }
            }
        } else {
            algorithms = new ArrayList<>(mdb.getAlgorithms());
        }

        NameDescriptionSearchMatcher matcher = request.hasQ() ? new NameDescriptionSearchMatcher(request.getQ()) : null;

        algorithms = algorithms.stream().filter(a -> {
            if (matcher != null && !matcher.matches(a)) {
                return false;
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
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.GetMissionDatabase);
        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Algorithm algo = RestHandler.verifyAlgorithm(mdb, request.getName());

        AlgorithmInfo cinfo = XtceToGpbAssembler.toAlgorithmInfo(algo, DetailLevel.FULL);
        observer.complete(cinfo);
    }

    private boolean parameterTypeMatches(Parameter p, List<String> types) {
        if (types.isEmpty()) {
            return true;
        }
        return p.getParameterType() != null
                && types.contains(p.getParameterType().getTypeAsString());
    }

    private boolean parameterSourceMatches(Parameter p, DataSourceType source) {
        DataSource xtceSource = p.getDataSource();
        return xtceSource != null && xtceSource.toString().equals(source.toString());
    }
}
