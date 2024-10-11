package org.yamcs.web.api;

import static org.yamcs.web.WebPlugin.CONFIG_DISPLAY_BUCKET;
import static org.yamcs.web.WebPlugin.CONFIG_PARAMETER_ARCHIVE;
import static org.yamcs.web.WebPlugin.CONFIG_STACK_BUCKET;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.InstancesApi;
import org.yamcs.http.api.StreamFactory;
import org.yamcs.parameterarchive.ParameterArchive;
import org.yamcs.web.WebPlugin;
import org.yamcs.web.db.Query;
import org.yamcs.web.db.QueryDb;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Empty;

/**
 * Extension routes to Yamcs HTTP API for use by the Web UI only.
 */
public class WebApi extends AbstractWebApi<Context> {

    /**
     * Get instance-level Web UI configuration options.
     */
    @Override
    public void getInstanceConfiguration(Context ctx, GetInstanceConfigurationRequest request,
            Observer<InstanceConfiguration> observer) {
        var yamcs = YamcsServer.getServer();
        var yamcsInstance = yamcs.getInstance(request.getInstance());
        if (yamcsInstance == null) {
            throw new NotFoundException("No such instance");
        }

        var pluginName = yamcs.getPluginManager().getMetadata(WebPlugin.class).getName();

        var globalConfig = yamcs.getConfig().getConfigOrEmpty(pluginName);
        var instanceConfig = yamcsInstance.getConfig().getConfigOrEmpty(pluginName);

        var b = InstanceConfiguration.newBuilder();

        var displayBucket = globalConfig.getString(CONFIG_DISPLAY_BUCKET);
        if (instanceConfig.containsKey(CONFIG_DISPLAY_BUCKET)) {
            displayBucket = instanceConfig.getString(CONFIG_DISPLAY_BUCKET);
        }
        b.setDisplayBucket(displayBucket);

        var stackBucket = globalConfig.getString(CONFIG_STACK_BUCKET);
        if (instanceConfig.containsKey(CONFIG_STACK_BUCKET)) {
            stackBucket = instanceConfig.getString(CONFIG_STACK_BUCKET);
        }
        b.setStackBucket(stackBucket);

        boolean parameterArchive;
        switch (instanceConfig.getString(CONFIG_PARAMETER_ARCHIVE, "auto")) {
        case "enabled":
            parameterArchive = true;
            break;
        case "disabled":
            parameterArchive = false;
            break;
        default:
            var services = yamcsInstance.getServices(ParameterArchive.class);
            if (services.isEmpty()) {
                parameterArchive = false;
            } else {
                parameterArchive = services.iterator().next().isRunning();
            }
        }
        b.setParameterArchive(parameterArchive);

        observer.complete(b.build());
    }

    @Override
    public void listQueries(Context ctx, ListQueriesRequest request, Observer<ListQueriesResponse> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());

        // Not used, but must make sure the table is created
        QueryDb.getInstance(instance);

        var sqlb = new SqlBuilder(QueryDb.TABLE_NAME);

        var queries = new ArrayList<Query>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                var query = new Query(tuple);
                queries.add(query);
            }

            @Override
            public void streamClosed(Stream stream) {
                Collections.sort(queries);

                var responseb = ListQueriesResponse.newBuilder();
                queries.forEach(query -> {
                    var info = toQueryInfo(ctx, query);
                    responseb.addQueries(info);
                });

                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void createQuery(Context ctx, CreateQueryRequest request, Observer<QueryInfo> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());

        var name = request.getName();
        var query = new Query(
                UUID.randomUUID(),
                request.getResource(),
                name,
                request.getShared() ? null : ctx.user.getId(),
                request.getQuery());

        var db = QueryDb.getInstance(instance);
        db.insert(query);
        observer.complete(toQueryInfo(ctx, query));
    }

    @Override
    public void updateQuery(Context ctx, UpdateQueryRequest request, Observer<QueryInfo> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());
        var db = QueryDb.getInstance(instance);
        var query = verifyQuery(db, request.getId());

        if (request.hasName()) {
            query.setName(request.getName());
        }
        if (request.hasShared()) {
            if (request.getShared()) {
                query.setUserId(null);
            } else {
                query.setUserId(ctx.user.getId());
            }
        }
        if (request.hasQuery()) {
            query.setQuery(request.getQuery());
        }

        db.update(query);
        observer.complete(toQueryInfo(ctx, query));
    }

    @Override
    public void deleteQuery(Context ctx, DeleteQueryRequest request, Observer<Empty> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());
        var db = QueryDb.getInstance(instance);
        var query = verifyQuery(db, request.getId());

        db.delete(query.getId());
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public Observer<ParseFilterRequest> parseFilter(Context ctx, Observer<ParseFilterData> observer) {
        var clientObserver = new ParseFilterObserver(observer);
        observer.setCancelHandler(() -> clientObserver.complete());
        return clientObserver;
    }

    private static QueryInfo toQueryInfo(Context ctx, Query query) {
        var queryb = QueryInfo.newBuilder()
                .setId(query.getId().toString())
                .setName(query.getName())
                .setShared(!Objects.equals(ctx.user.getId(), query.getUserId()))
                .setQuery(query.getQuery());
        return queryb.build();
    }

    public static Query verifyQuery(QueryDb db, String id) {
        var queryId = verifyId(id);
        var query = db.getById(queryId);
        if (query == null) {
            throw new NotFoundException("Query not found");
        }
        return query;
    }

    private static UUID verifyId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid identifier '" + id + "'");
        }
    }
}
