package org.yamcs.web;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.yamcs.CommandOption;
import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.http.Handler;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpServer;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.ServerApi;
import org.yamcs.http.auth.AuthHandler;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.AuthInfo;
import org.yamcs.templating.TemplateProcessor;

import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Handler that always responds with the contents of the index.html file of the webapp. The file is generated
 * dynamically because we do some templating on it.
 */
@Sharable
public class IndexHandler extends Handler {

    private YConfiguration config;
    private HttpServer httpServer;
    private Path indexFile;

    private String cachedHtml;
    private FileTime cacheTime;

    public IndexHandler(YConfiguration config, HttpServer httpServer, Path webRoot) {
        this.config = config;
        this.httpServer = httpServer;
        indexFile = webRoot.resolve("index.html");
    }

    @Override
    public void handle(HandlerContext ctx) {
        ctx.requireGET();

        if (!Files.exists(indexFile)) {
            throw new NotFoundException();
        }

        String html = null;
        try {
            html = getHtml();
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        ByteBuf body = ctx.createByteBuf();
        body.writeCharSequence(html, StandardCharsets.UTF_8);

        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());

        // Recommend clients to not cache this file. We hash all of our
        // web files, and this reduces likelihood of attempting to load
        // the app from an outdated index.html.
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store, must-revalidate");

        ctx.sendResponse(response);
    }

    private synchronized String getHtml() throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(indexFile);
        if (!lastModified.equals(cacheTime)) {
            cachedHtml = processTemplate();
            cacheTime = lastModified;
        }
        return cachedHtml;
    }

    @SuppressWarnings("unchecked")
    private String processTemplate() throws IOException {
        String template = new String(Files.readAllBytes(indexFile), StandardCharsets.UTF_8);

        Map<String, Object> webConfig = new HashMap<>(config.toMap());

        AuthInfo authInfo = AuthHandler.createAuthInfo();
        String authJson = JsonFormat.printer().print(authInfo);
        Map<String, Object> authMap = new Gson().fromJson(authJson, Map.class);
        webConfig.put("auth", authMap);

        YamcsServer yamcs = YamcsServer.getServer();

        List<Map<String, Object>> commandOptions = new ArrayList<>();
        for (CommandOption option : yamcs.getCommandOptions()) {
            String json = JsonFormat.printer().print(ServerApi.toCommandOptionInfo(option));
            commandOptions.add(new Gson().fromJson(json, Map.class));
        }
        webConfig.put("commandOptions", commandOptions);
        webConfig.put("serverId", yamcs.getServerId());
        webConfig.put("hasTemplates", !yamcs.getInstanceTemplates().isEmpty());

        // Enable clearance-related UI only if there's potential for a processor
        // that has it enabled (we expect most people to not use this feature).
        boolean commandClearanceEnabled = ProcessorFactory.getProcessorTypes().entrySet().stream()
                .anyMatch(entry -> entry.getValue().checkCommandClearance());
        webConfig.put("commandClearanceEnabled", commandClearanceEnabled);

        // Make queue names directly available without API request. It is used
        // for populating a command history combo box.
        SortedSet<String> queueNames = new TreeSet<>();
        for (CommandQueueManager qmanager : ManagementService.getInstance().getCommandQueueManagers()) {
            for (CommandQueue queue : qmanager.getQueues()) {
                queueNames.add(queue.getName());
            }
        }
        webConfig.put("queueNames", queueNames);

        Map<String, Object> args = new HashMap<>(4);
        args.put("contextPath", httpServer.getContextPath());
        args.put("config", webConfig);
        args.put("configJson", new Gson().toJson(webConfig));
        return TemplateProcessor.process(template, args);
    }
}
