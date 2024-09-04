package org.yamcs.http.api;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.plists.ParameterList;
import org.yamcs.plists.ParameterListDb;
import org.yamcs.plists.ParameterListService;
import org.yamcs.protobuf.plists.AbstractParameterListsApi;
import org.yamcs.protobuf.plists.CreateParameterListRequest;
import org.yamcs.protobuf.plists.DeleteParameterListRequest;
import org.yamcs.protobuf.plists.GetParameterListRequest;
import org.yamcs.protobuf.plists.ListParameterListsRequest;
import org.yamcs.protobuf.plists.ListParameterListsResponse;
import org.yamcs.protobuf.plists.ParameterListInfo;
import org.yamcs.protobuf.plists.UpdateParameterListRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Empty;

public class ParameterListsApi extends AbstractParameterListsApi<Context> {

    private static final Log log = new Log(ParameterListsApi.class);

    @Override
    public void listParameterLists(Context ctx, ListParameterListsRequest request,
            Observer<ListParameterListsResponse> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());
        verifyService(instance);
        var mdb = MdbFactory.getInstance(instance);

        var sqlb = new SqlBuilder(ParameterListDb.TABLE_NAME);

        var plists = new ArrayList<ParameterList>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                var plist = new ParameterList(tuple);
                plists.add(plist);
            }

            @Override
            public void streamClosed(Stream stream) {
                Collections.sort(plists);

                var responseb = ListParameterListsResponse.newBuilder();
                plists.forEach(plist -> {
                    var info = toParameterListInfo(ctx, mdb, plist, false);
                    responseb.addLists(info);
                });

                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getParameterList(Context ctx, GetParameterListRequest request, Observer<ParameterListInfo> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());
        var plistService = verifyService(instance);
        var plist = verifyParameterList(plistService, request.getList());
        var mdb = MdbFactory.getInstance(instance);
        observer.complete(toParameterListInfo(ctx, mdb, plist, true));
    }

    @Override
    public void createParameterList(Context ctx, CreateParameterListRequest request,
            Observer<ParameterListInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageParameterLists);
        var instance = InstancesApi.verifyInstance(request.getInstance());
        var plistService = verifyService(instance);
        var db = plistService.getParameterListDb();
        var mdb = MdbFactory.getInstance(instance);

        if (!request.hasName()) {
            throw new BadRequestException("Name is required");
        }
        var name = request.getName().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Name is required");
        }

        var plist = new ParameterList(UUID.randomUUID(), name);
        if (request.hasDescription()) {
            plist.setDescription(request.getDescription());
        }
        plist.setPatterns(request.getPatternsList());

        db.insert(plist);
        observer.complete(toParameterListInfo(ctx, mdb, plist, true));
    }

    @Override
    public void updateParameterList(Context ctx, UpdateParameterListRequest request,
            Observer<ParameterListInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageParameterLists);
        var instance = InstancesApi.verifyInstance(request.getInstance());
        var plistService = verifyService(instance);
        var db = plistService.getParameterListDb();
        var mdb = MdbFactory.getInstance(instance);
        var plist = verifyParameterList(plistService, request.getList());

        if (request.hasName()) {
            var newName = request.getName().trim();
            if (newName.isEmpty()) {
                throw new BadRequestException("Name must not be empty");
            }
            plist.setName(newName);
        }
        if (request.hasDescription()) {
            plist.setDescription(request.getDescription());
        }
        if (request.hasPatternDefinition()) {
            plist.setPatterns(request.getPatternDefinition().getPatternsList());
        }

        db.update(plist);
        observer.complete(toParameterListInfo(ctx, mdb, plist, true));
    }

    @Override
    public void deleteParameterList(Context ctx, DeleteParameterListRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ManageParameterLists);
        var plistService = verifyService(request.getInstance());
        var db = plistService.getParameterListDb();
        var plistId = verifyId(request.getList());

        db.delete(plistId);
        observer.complete(Empty.getDefaultInstance());
    }

    private static ParameterListInfo toParameterListInfo(Context ctx, Mdb mdb, ParameterList plist,
            boolean addResolvedParameters) {
        var plistb = ParameterListInfo.newBuilder()
                .setId(plist.getId().toString())
                .setName(plist.getName());
        if (plist.getDescription() != null) {
            plistb.setDescription(plist.getDescription());
        }
        plistb.addAllPatterns(plist.getPatterns());

        if (addResolvedParameters) {
            for (var parameter : resolveParameters(ctx, mdb, plist)) {
                var pinfo = XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.SUMMARY);
                plistb.addMatch(pinfo);
            }
        }

        return plistb.build();
    }

    public static List<Parameter> resolveParameters(Context ctx, Mdb mdb, ParameterList plist) {
        var parameters = new ArrayList<Parameter>();
        for (String p : plist.getPatterns()) {
            if (p.endsWith("/")) {
                var system = mdb.getSpaceSystem(p.substring(0, p.length() - 1));
                if (system == null) {
                    continue;
                }
                system.getParameters().forEach(parameters::add);
            } else {
                var matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                for (var candidate : mdb.getParameters()) {
                    if (matcher.matches(Path.of(candidate.getQualifiedName()))) {
                        parameters.add(candidate);
                    }
                }
            }
        }

        return parameters.stream()
                .filter(p -> ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName()))
                .collect(Collectors.toList());
    }

    public static ParameterListService verifyService(String yamcsInstance) {
        String instance = InstancesApi.verifyInstance(yamcsInstance);

        var services = YamcsServer.getServer().getInstance(instance)
                .getServices(ParameterListService.class);
        if (services.isEmpty()) {
            throw new NotFoundException("No parameter list service found");
        } else {
            if (services.size() > 1) {
                log.warn("Multiple parameter list services found but only one supported");
            }
            return services.get(0);
        }
    }

    public static ParameterList verifyParameterList(ParameterListService plistService, String id) {
        var plistId = verifyId(id);
        var plist = plistService.getParameterListDb().getById(plistId);
        if (plist == null) {
            throw new NotFoundException("Parameter list not found");
        }
        return plist;
    }

    private static UUID verifyId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid identifier '" + id + "'");
        }
    }
}
