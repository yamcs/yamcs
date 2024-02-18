package org.yamcs.web;

import static org.yamcs.web.WebPlugin.CONFIG_DISPLAY_BUCKET;
import static org.yamcs.web.WebPlugin.CONFIG_SECTION;
import static org.yamcs.web.WebPlugin.CONFIG_STACK_BUCKET;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.web.api.AbstractWebApi;
import org.yamcs.web.api.GetInstanceConfigurationRequest;
import org.yamcs.web.api.InstanceConfiguration;

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

        var globalConfig = yamcs.getConfig().getConfigOrEmpty(CONFIG_SECTION);
        var instanceConfig = yamcsInstance.getConfig().getConfigOrEmpty(CONFIG_SECTION);

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

        observer.complete(b.build());
    }
}
