package org.yamcs.http.api;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.CommandOption;
import org.yamcs.Plugin;
import org.yamcs.PluginManager;
import org.yamcs.PluginMetadata;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.YamcsVersion;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpServer;
import org.yamcs.http.MediaType;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.Route;
import org.yamcs.http.RpcDescriptor;
import org.yamcs.http.Topic;
import org.yamcs.http.WebSocketFrameHandler;
import org.yamcs.protobuf.AbstractServerApi;
import org.yamcs.protobuf.ClientConnectionInfo;
import org.yamcs.protobuf.ClientConnectionInfo.HttpRequestInfo;
import org.yamcs.protobuf.GetServerInfoResponse;
import org.yamcs.protobuf.GetServerInfoResponse.CommandOptionInfo;
import org.yamcs.protobuf.GetServerInfoResponse.PluginInfo;
import org.yamcs.protobuf.GetThreadRequest;
import org.yamcs.protobuf.HttpTraffic;
import org.yamcs.protobuf.ListRoutesResponse;
import org.yamcs.protobuf.ListThreadsRequest;
import org.yamcs.protobuf.ListThreadsResponse;
import org.yamcs.protobuf.ListTopicsResponse;
import org.yamcs.protobuf.RouteInfo;
import org.yamcs.protobuf.ThreadGroupInfo;
import org.yamcs.protobuf.ThreadInfo;
import org.yamcs.protobuf.TopicInfo;
import org.yamcs.protobuf.TraceElementInfo;
import org.yamcs.security.SystemPrivilege;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

public class ServerApi extends AbstractServerApi<Context> {

    private HttpServer httpServer;

    public ServerApi(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    public void getServerInfo(Context ctx, Empty request, Observer<GetServerInfoResponse> observer) {
        GetServerInfoResponse.Builder responseb = GetServerInfoResponse.newBuilder();
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

        for (CommandOption option : YamcsServer.getServer().getCommandOptions()) {
            responseb.addCommandOptions(toCommandOptionInfo(option));
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
            List<YamcsServerInstance> instances = YamcsServer.getInstances();
            if (!instances.isEmpty()) {
                YamcsServerInstance anyInstance = instances.iterator().next();
                responseb.setDefaultYamcsInstance(anyInstance.getName());
            }
        }

        observer.complete(responseb.build());
    }

    public static CommandOptionInfo toCommandOptionInfo(CommandOption option) {
        CommandOptionInfo.Builder infob = CommandOptionInfo.newBuilder();
        infob.setId(option.getId());
        infob.setType(option.getType().name());
        if (option.getVerboseName() != null) {
            infob.setVerboseName(option.getVerboseName());
        }
        if (option.getHelp() != null) {
            infob.setHelp(option.getHelp());
        }
        return infob.build();
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
                if (route.getLogFormat() != null) {
                    routeb.setLogFormat(route.getLogFormat());
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
    public void getHttpTraffic(Context ctx, Empty request, Observer<HttpTraffic> observer) {
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }

        HttpTraffic.Builder responseb = HttpTraffic.newBuilder();

        GlobalTrafficShapingHandler globalTrafficHandler = httpServer.getGlobalTrafficShapingHandler();
        if (globalTrafficHandler != null) {
            TrafficCounter counter = globalTrafficHandler.trafficCounter();
            if (counter != null) {
                responseb.setReadThroughput(counter.lastReadThroughput());
                responseb.setWriteThroughput(counter.lastWriteThroughput());
                responseb.setReadBytes(counter.cumulativeReadBytes());
                responseb.setWrittenBytes(counter.cumulativeWrittenBytes());
            }
        }

        List<ClientConnectionInfo> result = new ArrayList<>();
        for (Channel channel : httpServer.getClientChannels()) {
            HttpRequest httpRequest = channel.attr(HttpRequestHandler.CTX_HTTP_REQUEST).get();
            if (httpRequest == null) {
                continue; // Could be in the process of being handled
            }

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

            String username = channel.attr(HttpRequestHandler.CTX_USERNAME).get();
            if (username != null) {
                connectionb.setUsername(username);
            }

            String protocol = httpRequest.protocolVersion().text();
            if (channel.pipeline().get(WebSocketFrameHandler.class) != null) {
                protocol = "WebSocket";
            }
            HttpRequestInfo.Builder httpRequestb = HttpRequestInfo.newBuilder()
                    .setKeepAlive(HttpUtil.isKeepAlive(httpRequest))
                    .setProtocol(protocol)
                    .setMethod(httpRequest.method().name())
                    .setUri(httpRequest.uri());
            String userAgent = httpRequest.headers().getAsString(HttpHeaderNames.USER_AGENT);
            if (userAgent != null) {
                httpRequestb.setUserAgent(userAgent);
            }

            connectionb.setHttpRequest(httpRequestb.build());
            result.add(connectionb.build());
        }

        responseb.addAllConnections(result);
        observer.complete(responseb.build());
    }

    @Override
    public void listThreads(Context ctx, ListThreadsRequest request, Observer<ListThreadsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadThreads);

        ListThreadsResponse.Builder responseb = ListThreadsResponse.newBuilder();

        // Try to acquire group information only available from the actual Thread object
        Map<Long, ThreadGroupInfo> groupsById = new HashMap<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            ThreadGroup group = thread.getThreadGroup();
            if (group != null) {
                groupsById.put(thread.getId(), toThreadGroupInfo(group));
            }
        }

        // Use MXBean for the actual dump, inject group info if (still) matched
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        for (java.lang.management.ThreadInfo managementInfo : bean.dumpAllThreads(false, false)) {
            ThreadGroupInfo group = groupsById.get(managementInfo.getThreadId());
            responseb.addThreads(toThreadInfo(managementInfo, group));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void dumpThreads(Context ctx, Empty request, Observer<HttpBody> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadThreads);

        ByteString.Output out = ByteString.newOutput();
        ThreadMXBean mxbean = ManagementFactory.getThreadMXBean();
        try {
            for (java.lang.management.ThreadInfo threadInfo : mxbean.dumpAllThreads(false, false)) {
                String dump = describeThread(threadInfo);
                out.write(dump.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            observer.completeExceptionally(e);
            return;
        }

        String timestamp = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        observer.next(HttpBody.newBuilder()
                .setFilename("thread-dump-" + timestamp.replace(':', '-') + ".txt")
                .setContentType(MediaType.PLAIN_TEXT.toString())
                .setData(out.toByteString())
                .build());
    }

    // Same as ThreadInfo.toString but without stack trace limitation
    private String describeThread(java.lang.management.ThreadInfo thread) {
        StringBuilder sb = new StringBuilder("\"" + thread.getThreadName() + "\"" +
                " Id=" + thread.getThreadId() + " " +
                thread.getThreadState());
        if (thread.getLockName() != null) {
            sb.append(" on " + thread.getLockName());
        }
        if (thread.getLockOwnerName() != null) {
            sb.append(" owned by \"" + thread.getLockOwnerName() +
                    "\" Id=" + thread.getLockOwnerId());
        }
        if (thread.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (thread.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');

        StackTraceElement[] stackTrace = thread.getStackTrace();
        for (int i = 0; i < stackTrace.length && i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && thread.getLockInfo() != null) {
                Thread.State ts = thread.getThreadState();
                switch (ts) {
                case BLOCKED:
                    sb.append("\t-  blocked on " + thread.getLockInfo());
                    sb.append('\n');
                    break;
                case WAITING:
                    sb.append("\t-  waiting on " + thread.getLockInfo());
                    sb.append('\n');
                    break;
                case TIMED_WAITING:
                    sb.append("\t-  waiting on " + thread.getLockInfo());
                    sb.append('\n');
                    break;
                default:
                }
            }
        }

        return sb.append('\n').toString();
    }

    @Override
    public void getThread(Context ctx, GetThreadRequest request, Observer<ThreadInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadThreads);

        ThreadGroupInfo groupInfo = null;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getId() == request.getId()) {
                ThreadGroup group = thread.getThreadGroup();
                if (group != null) {
                    groupInfo = toThreadGroupInfo(group);
                }
                break;
            }
        }

        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        java.lang.management.ThreadInfo managementInfo = bean.getThreadInfo(request.getId(), Integer.MAX_VALUE);
        if (managementInfo != null) {
            observer.complete(toThreadInfo(managementInfo, groupInfo));
        } else {
            throw new NotFoundException("No thread with ID " + request.getId());
        }
    }

    private ThreadGroupInfo toThreadGroupInfo(ThreadGroup group) {
        ThreadGroupInfo.Builder b = ThreadGroupInfo.newBuilder()
                .setName(group.getName())
                .setDaemon(group.isDaemon());
        ThreadGroup parent = group.getParent();
        if (parent != null) {
            b.setParent(toThreadGroupInfo(parent));
        }
        return b.build();
    }

    private ThreadInfo toThreadInfo(java.lang.management.ThreadInfo threadInfo, ThreadGroupInfo group) {
        ThreadInfo.Builder threadb = ThreadInfo.newBuilder()
                .setId(threadInfo.getThreadId())
                .setName(threadInfo.getThreadName())
                .setState(threadInfo.getThreadState().name())
                .setNative(threadInfo.isInNative())
                .setSuspended(threadInfo.isSuspended());

        StackTraceElement[] trace = threadInfo.getStackTrace();
        for (int i = 0; i < trace.length; i++) {
            StackTraceElement traceEl = trace[i];
            TraceElementInfo.Builder elb = TraceElementInfo.newBuilder()
                    .setClassName(traceEl.getClassName())
                    .setMethodName(traceEl.getMethodName());
            String fileName = traceEl.getFileName();
            if (fileName != null) {
                elb.setFileName(fileName);
            }
            int lineNumber = traceEl.getLineNumber();
            if (lineNumber >= 0) {
                elb.setLineNumber(lineNumber);
            }
            threadb.addTrace(elb);
        }

        if (group != null) {
            threadb.setGroup(group);
        }

        return threadb.build();
    }
}
