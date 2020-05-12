package org.yamcs.http.api;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.yamcs.Plugin;
import org.yamcs.PluginManager;
import org.yamcs.PluginMetadata;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.YamcsVersion;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpServer;
import org.yamcs.http.Route;
import org.yamcs.http.RpcDescriptor;
import org.yamcs.http.Topic;
import org.yamcs.protobuf.AbstractGeneralApi;
import org.yamcs.protobuf.ClientConnectionInfo;
import org.yamcs.protobuf.ClientConnectionInfo.HttpRequestInfo;
import org.yamcs.protobuf.CloseConnectionRequest;
import org.yamcs.protobuf.GetGeneralInfoResponse;
import org.yamcs.protobuf.GetGeneralInfoResponse.PluginInfo;
import org.yamcs.protobuf.ListClientConnectionsResponse;
import org.yamcs.protobuf.ListRoutesResponse;
import org.yamcs.protobuf.ListTopicsResponse;
import org.yamcs.protobuf.RouteInfo;
import org.yamcs.protobuf.TopicInfo;

import com.google.protobuf.Empty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

public class GeneralApi extends AbstractGeneralApi<Context> {

    private HttpServer httpServer;

    public GeneralApi(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    public void getGeneralInfo(Context ctx, Empty request, Observer<GetGeneralInfoResponse> observer) {
        GetGeneralInfoResponse.Builder responseb = GetGeneralInfoResponse.newBuilder();
        responseb.setYamcsVersion(YamcsVersion.VERSION);
        responseb.setRevision(YamcsVersion.REVISION);
        responseb.setServerId(YamcsServer.getServer().getServerId());

        PluginManager pluginManager = YamcsServer.getServer().getPluginManager();
        List<Plugin> plugins = new ArrayList<>(pluginManager.getPlugins());
        List<PluginInfo> pluginInfos = new ArrayList<>();
        for (Plugin plugin : plugins) {
            PluginMetadata meta = pluginManager.getMetadata(plugin.getClass());
            PluginInfo.Builder pluginb = PluginInfo.newBuilder()
                    .setName(meta.getName());
            if (meta.getVersion() != null) {
                pluginb.setVersion(meta.getVersion());
            }
            if (meta.getOrganization() != null) {
                pluginb.setVendor(meta.getOrganization());
            }
            if (meta.getDescription() != null) {
                pluginb.setDescription(meta.getDescription());
            }
            pluginInfos.add(pluginb.build());
        }

        pluginInfos.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));
        responseb.addAllPlugins(pluginInfos);

        // Property to be interpreted at client's leisure.
        // Concept of defaultInstance could be moved into YamcsServer
        // at some point, but there's for now unsufficient support.
        // (would need websocket adjustments, which are now
        // instance-specific).
        YConfiguration yconf = YamcsServer.getServer().getConfig();
        if (yconf.containsKey("defaultInstance")) {
            responseb.setDefaultYamcsInstance(yconf.getString("defaultInstance"));
        } else {
            Set<YamcsServerInstance> instances = YamcsServer.getInstances();
            if (!instances.isEmpty()) {
                YamcsServerInstance anyInstance = instances.iterator().next();
                responseb.setDefaultYamcsInstance(anyInstance.getName());
            }
        }

        observer.complete(responseb.build());
    }

    @Override
    public void listRoutes(Context ctx, Empty request, Observer<ListRoutesResponse> observer) {
        List<RouteInfo> result = new ArrayList<>();
        for (Route route : httpServer.getRoutes()) {
            RouteInfo.Builder routeb = RouteInfo.newBuilder();
            routeb.setHttpMethod(route.getHttpMethod().toString());
            routeb.setUrl(httpServer.getContextPath() + route.getUriTemplate());
            routeb.setRequestCount(route.getRequestCount());
            routeb.setErrorCount(route.getErrorCount());
            RpcDescriptor descriptor = route.getDescriptor();
            if (descriptor != null) {
                routeb.setService(descriptor.getService());
                routeb.setMethod(descriptor.getMethod());
                routeb.setInputType(descriptor.getInputType().getName());
                routeb.setOutputType(descriptor.getOutputType().getName());
                if (descriptor.getDescription() != null) {
                    routeb.setDescription(descriptor.getDescription());
                }
                if (route.isDeprecated()) {
                    routeb.setDeprecated(true);
                }
            }
            result.add(routeb.build());
        }

        Collections.sort(result, (r1, r2) -> {
            int rc = r1.getUrl().compareToIgnoreCase(r2.getUrl());
            return rc != 0 ? rc : r1.getMethod().compareTo(r2.getMethod());
        });

        ListRoutesResponse.Builder responseb = ListRoutesResponse.newBuilder();
        responseb.addAllRoutes(result);
        observer.complete(responseb.build());
    }

    @Override
    public void listTopics(Context ctx, Empty request, Observer<ListTopicsResponse> observer) {
        List<TopicInfo> result = new ArrayList<>();
        for (Topic topic : httpServer.getTopics()) {
            TopicInfo.Builder topicb = TopicInfo.newBuilder()
                    .setTopic(topic.getName());
            RpcDescriptor descriptor = topic.getDescriptor();
            if (descriptor != null) {
                topicb.setService(descriptor.getService());
                topicb.setMethod(descriptor.getMethod());
                topicb.setInputType(descriptor.getInputType().getName());
                topicb.setOutputType(descriptor.getOutputType().getName());
                if (descriptor.getDescription() != null) {
                    topicb.setDescription(descriptor.getDescription());
                }
                if (topic.isDeprecated()) {
                    topicb.setDeprecated(true);
                }
            }
            result.add(topicb.build());
        }

        Collections.sort(result, (r1, r2) -> {
            return r1.getTopic().compareToIgnoreCase(r2.getTopic());
        });

        ListTopicsResponse.Builder responseb = ListTopicsResponse.newBuilder();
        responseb.addAllTopics(result);
        observer.complete(responseb.build());
    }

    @Override
    public void listClientConnections(Context ctx, Empty request, Observer<ListClientConnectionsResponse> observer) {
        List<ClientConnectionInfo> result = new ArrayList<>();
        for (Channel channel : httpServer.getClientChannels()) {
            ClientConnectionInfo.Builder connectionb = ClientConnectionInfo.newBuilder()
                    .setId(channel.id().asShortText())
                    .setOpen(channel.isOpen())
                    .setActive(channel.isActive())
                    .setWritable(channel.isWritable());

            InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
            if (address != null) {
                connectionb.setRemoteAddress(address.getAddress().getHostAddress() + ":" + address.getPort());
            }

            ChannelTrafficShapingHandler trafficHandler = channel.pipeline()
                    .get(ChannelTrafficShapingHandler.class);
            if (trafficHandler != null) {
                TrafficCounter counter = trafficHandler.trafficCounter();
                if (counter != null) {
                    connectionb.setReadThroughput(counter.lastReadThroughput());
                    connectionb.setWriteThroughput(counter.lastWriteThroughput());
                    connectionb.setReadBytes(counter.cumulativeReadBytes());
                    connectionb.setWrittenBytes(counter.cumulativeWrittenBytes());
                }
            }

            HttpRequest httpRequest = channel.attr(HttpRequestHandler.CTX_HTTP_REQUEST).get();
            if (httpRequest != null) {
                HttpRequestInfo.Builder httpRequestb = HttpRequestInfo.newBuilder()
                        .setKeepAlive(HttpUtil.isKeepAlive(httpRequest))
                        .setProtocol(httpRequest.protocolVersion().text())
                        .setMethod(httpRequest.method().name())
                        .setUri(httpRequest.uri());
                String userAgent = httpRequest.headers().getAsString("User-Agent");
                if (userAgent != null) {
                    httpRequestb.setUserAgent(userAgent);
                }

                connectionb.setHttpRequest(httpRequestb.build());
            }

            result.add(connectionb.build());
        }

        ListClientConnectionsResponse.Builder responseb = ListClientConnectionsResponse.newBuilder();
        responseb.addAllConnections(result);
        observer.complete(responseb.build());
    }

    @Override
    public void closeConnection(Context ctx, CloseConnectionRequest request, Observer<Empty> observer) {
        httpServer.closeChannel(request.getId());
        observer.complete(Empty.getDefaultInstance());
    }
}
