package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_HTML;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpServer;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.StaticFileHandler;
import org.yamcs.http.api.ServerApi;
import org.yamcs.http.auth.AuthHandler;
import org.yamcs.management.ManagementService;
import org.yamcs.templating.ParseException;
import org.yamcs.templating.TemplateProcessor;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

public class AngularHandler extends StaticFileHandler {

    private YConfiguration config;
    private HttpServer httpServer;

    private Path indexFile;
    private String cachedHtml;
    private FileTime cacheTime;

    private String logo;
    private Path logoFile;

    public AngularHandler(YConfiguration config, HttpServer httpServer, Path staticRoot) {
        super("", staticRoot);
        this.config = config;
        this.httpServer = httpServer;
        indexFile = staticRoot.resolve("index.html");

        if (config.containsKey("logo")) {
            logoFile = Path.of(config.getString("logo"));
            logo = logoFile.getFileName().toString();
        }
    }

    @Override
    public void handle(HandlerContext ctx) {
        var filePath = getFilePath(ctx);

        // Serve a logo image, if so configured
        if (logo != null && logo.equals(filePath)) {
            serveLogo(ctx);
            return;
        }

        // Try to serve a static file
        var file = locateFile(filePath);
        if (file != null && !filePath.isEmpty()) {
            super.handle(ctx);
            return;
        }

        // Set-up HTML5 deep-linking:
        // Catch any non-handled URL and make it return the contents of our index.html
        // This will cause initialization of the Angular app on any requested path. The
        // Angular router will interpret this and do client-side routing as needed.
        serveIndex(ctx);
    }

    private void serveLogo(HandlerContext ctx) {
        ctx.requireGET();

        var body = ctx.createByteBuf();
        try (var in = Files.newInputStream(logoFile); var out = new ByteBufOutputStream(body)) {
            ByteStreams.copy(in, out);
        } catch (NoSuchFileException e) {
            throw new NotFoundException();
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        var response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        response.headers().set(CONTENT_TYPE, MIME.getMimetype(logoFile));
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=86400");
        ctx.sendResponse(response);
    }

    private void serveIndex(HandlerContext ctx) {
        ctx.requireGET();

        if (!Files.exists(indexFile)) {
            throw new NotFoundException();
        }

        String html = null;
        try {
            html = renderHtml();
        } catch (IOException | ParseException e) {
            throw new InternalServerErrorException(e);
        }

        var body = ctx.createByteBuf();
        body.writeCharSequence(html, UTF_8);

        var response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        response.headers().set(CONTENT_TYPE, TEXT_HTML);
        response.headers().set(CONTENT_LENGTH, body.readableBytes());

        // Recommend clients to not cache this file. We hash all of our
        // web files, and this reduces likelihood of attempting to load
        // the app from an outdated index.html.
        response.headers().set(CACHE_CONTROL, "no-store, must-revalidate");

        ctx.sendResponse(response);
    }

    private synchronized String renderHtml() throws IOException, ParseException {
        var lastModified = Files.getLastModifiedTime(indexFile);
        if (!lastModified.equals(cacheTime)) {
            cachedHtml = renderHtmlTemplate();
            cacheTime = lastModified;
        }
        return cachedHtml;
    }

    @SuppressWarnings("unchecked")
    private String renderHtmlTemplate() throws IOException, ParseException {
        var template = new String(Files.readAllBytes(indexFile), StandardCharsets.UTF_8);

        // Don't use template for this, because Angular compiler messes up the HTML
        template = template.replace("<!--[[ EXTRA_HEADER_HTML ]]-->", config.getString("extraHeaderHTML", ""));

        var webConfig = new HashMap<>(config.toMap());

        // Remove filesystem path from custom logo
        if (config.containsKey("logo")) {
            var file = Path.of(config.getString("logo"));
            var filename = file.getFileName().toString();
            webConfig.put("logo", httpServer.getContextPath() + "/" + filename);
        }

        var authInfo = AuthHandler.createAuthInfo();
        var authJson = JsonFormat.printer().print(authInfo);
        var authMap = new Gson().fromJson(authJson, Map.class);
        webConfig.put("auth", authMap);

        var yamcs = YamcsServer.getServer();

        var commandOptions = new ArrayList<Map<String, Object>>();
        for (var option : yamcs.getCommandOptions()) {
            var json = JsonFormat.printer().print(ServerApi.toCommandOptionInfo(option));
            commandOptions.add(new Gson().fromJson(json, Map.class));
        }
        webConfig.put("commandOptions", commandOptions);
        webConfig.put("serverId", yamcs.getServerId());
        webConfig.put("hasTemplates", !yamcs.getInstanceTemplates().isEmpty());

        // Enable clearance-related UI only if there's potential for a processor
        // that has it enabled (we expect most people to not use this feature).
        var commandClearanceEnabled = ProcessorFactory.getProcessorTypes().entrySet().stream()
                .anyMatch(entry -> entry.getValue().checkCommandClearance());
        webConfig.put("commandClearanceEnabled", commandClearanceEnabled);

        // Make queue names directly available without API request. It is used
        // for populating a command history combo box.
        var queueNames = new TreeSet<String>(); // Sorted
        for (var qmanager : ManagementService.getInstance().getCommandQueueManagers()) {
            for (var queue : qmanager.getQueues()) {
                queueNames.add(queue.getName());
            }
        }
        webConfig.put("queueNames", queueNames);

        var args = new HashMap<String, Object>();
        args.put("contextPath", httpServer.getContextPath());
        args.put("config", webConfig);
        args.put("configJson", new Gson().toJson(webConfig));
        return TemplateProcessor.process(template, args);
    }
}
