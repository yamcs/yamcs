package org.yamcs;

import java.io.IOException;

import org.yamcs.http.HttpServer;
import org.yamcs.logging.Log;

public abstract class AbstractPlugin implements Plugin {

    protected final Log log = new Log(getClass());
    protected final YamcsServer yamcs = YamcsServer.getServer();
    protected String pluginName;
    protected String pluginVersion;
    protected YConfiguration config;

    @Override
    public final void onLoad(YConfiguration config) throws PluginException {
        this.config = config;

        var metadata = yamcs.getPluginManager().getMetadata(getClass());
        pluginName = metadata.getName();
        pluginVersion = metadata.getVersion();

        importProtobufDefinitions();
        init();
    }

    public abstract void init() throws PluginException;

    /**
     * Attempt to load a binary description of the protobuf definitions, if found at the expected location. Or fail
     * quietly.
     */
    private void importProtobufDefinitions() throws PluginException {
        var httpServer = yamcs.getGlobalService(HttpServer.class);
        if (httpServer != null) {
            try (var in = getClass().getResourceAsStream("/" + pluginName + ".protobin")) {
                if (in != null) {
                    log.trace("Loading {} protobuf definitions", pluginName);
                    httpServer.getProtobufRegistry().importDefinitions(in);
                }
            } catch (IOException e) {
                throw new PluginException(e);
            }
        }
    }

    public Log getLog() {
        return log;
    }

    public YConfiguration getConfig() {
        return config;
    }
}
