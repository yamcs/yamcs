package org.yamcs.web.rest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.WebConfig;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;

/** 
 * provides information about available displays
 * 
 * Currently the only method supported is "list"
 * @author nm
 *
 */
public class DisplayRestHandler extends RestHandler {
    JsonFactory jsonFactory=new JsonFactory();
    private final static Logger log=LoggerFactory.getLogger(DisplayRestHandler.class);

    @Route(path="/api/displays/:instance", method="GET")
    public void listDisplays(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        ByteBuf cb=req.getChannelHandlerContext().alloc().buffer(1024);
        ByteBufOutputStream cbos=new ByteBufOutputStream(cb);
        
        try (JsonGenerator json = jsonFactory.createGenerator(cbos, JsonEncoding.UTF8)) {
            json.writeStartArray();
            
            File displayDir = null;
            for (String webRoot : WebConfig.getInstance().getWebRoots()) {
                File dir = new File(webRoot + File.separator + instance + File.separator + "displays");
                if (dir.exists()) {
                    displayDir = dir;
                    break;
                }
            }
            if (displayDir != null) {
                writeFilesFromDir(json, new Path(), displayDir);
            }
            json.close();
            completeOK(req, MediaType.JSON, cb);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
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
        ArrayList<String> list=new ArrayList<>();
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
