package org.yamcs.web;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * provides information about available displays
 * 
 * Currently the only method supported is "list"
 * @author nm
 *
 */
public class DisplayRequestHandler extends AbstractRequestHandler {
    JsonFactory jsonFactory=new JsonFactory();
    final static Logger log=LoggerFactory.getLogger(WebSocketServerHandler.class.getName());
    
    StaticFileRequestHandler fileRequestHandler;
    
    public DisplayRequestHandler(StaticFileRequestHandler fileRequestHandler) {
        this.fileRequestHandler=fileRequestHandler;
    }

    void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent e, String yamcsInstance, String remainingUri) throws Exception {
        if((remainingUri==null) || remainingUri.isEmpty()) {
            fileRequestHandler.handleStaticFileRequest(ctx, req, e, "uss-app.html");
            return;
        } else if (remainingUri.contains("/")){
            sendError(ctx, BAD_REQUEST);
        } else if("listDisplays".equals(remainingUri)) {
            handleListDisplays(req, e);
        } else {
            fileRequestHandler.handleStaticFileRequest(ctx, req, e,  "single-display.html");
        }
    }

    private void handleListDisplays(HttpRequest req, MessageEvent e) throws IOException {
        ChannelBuffer cb=ChannelBuffers.buffer(1024);
        ChannelBufferOutputStream cbos=new ChannelBufferOutputStream(cb);
        
        JsonGenerator json=jsonFactory.createJsonGenerator(cbos, JsonEncoding.UTF8);
        json.writeStartArray();
        writeFilesFromDir(json, new Path(), new File(StaticFileRequestHandler.WEB_Root+"/displays"));
        json.close();
        
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentTypeHeader(response, JSON_MIME_TYPE);
        setContentLength(response, cb.readableBytes());
        response.setContent(cb);
        
        Channel ch = e.getChannel();
        ChannelFuture writeFuture=ch.write(response);

        // Decide whether to close the connection or not.
        if (!isKeepAlive(req)) {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
 
    }
    private void writeFilesFromDir(JsonGenerator json, Path path, File f) throws JsonGenerationException, IOException {
        if(!f.isDirectory()) {
            log.warn("Supposed to list all files from '{}' but it's not a directory", f);
            return;
        }
        //all this complicated stack in order to not write empty directories and not have an intermediate tree to store the result
        File[] files=f.listFiles();
        for(File f1: files) {
            if(f1.isDirectory()) {
                path.push(f1.getName());
                writeFilesFromDir(json, path, f1);
                path.pop();
            } else if(f1.getName().endsWith(".uss")){
                if(path.index<path.size()) {
                    while(path.index < path.size()) {
                        json.writeStartArray();
                        json.writeString(path.get(path.index));
                        path.index++;
                    }
                }
                json.writeString(f1.getName());
            }
        }
        while(path.index>=path.size()) {
            json.writeEndArray();
            path.index--;
        }
    }
    
    private static class Path {
        int index=0;
        ArrayList<String> list=new ArrayList<String>();
        public void push(String name) {
            list.add(name);
        }
        public String get(int idx) {
            return list.get(idx);
        }
        public int size() {
            return list.size();
        }
        public void pop() {
            list.remove(list.size()-1);
        }
    }
}
