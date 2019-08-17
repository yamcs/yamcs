package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.yamcs.Plugin;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.YamcsVersion;
import org.yamcs.api.Observer;
import org.yamcs.protobuf.AbstractGeneralApi;
import org.yamcs.protobuf.GetGeneralInfoResponse;
import org.yamcs.protobuf.GetGeneralInfoResponse.PluginInfo;
import org.yamcs.protobuf.ListRoutesResponse;
import org.yamcs.protobuf.RouteInfo;

import com.google.protobuf.Empty;

public class GeneralApi extends AbstractGeneralApi<Context> {

    private Router router;

    public GeneralApi(Router router) {
        this.router = router;
    }

    @Override
    public void getGeneralInfo(Context ctx, Empty request, Observer<GetGeneralInfoResponse> observer) {
        GetGeneralInfoResponse.Builder responseb = GetGeneralInfoResponse.newBuilder();
        responseb.setYamcsVersion(YamcsVersion.VERSION);
        responseb.setRevision(YamcsVersion.REVISION);
        responseb.setServerId(YamcsServer.getServer().getServerId());

        List<Plugin> plugins = new ArrayList<>(YamcsServer.getServer().getPlugins());
        plugins.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));
        for (Plugin plugin : plugins) {
            PluginInfo.Builder pluginb = PluginInfo.newBuilder()
                    .setName(plugin.getName());
            if (plugin.getVersion() != null) {
                pluginb.setVersion(plugin.getVersion());
            }
            if (plugin.getVendor() != null) {
                pluginb.setVendor(plugin.getVendor());
            }
            if (plugin.getDescription() != null) {
                pluginb.setDescription(plugin.getDescription());
            }
            responseb.addPlugins(pluginb);
        }

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
        List<RouteInfo> routes = router.getRouteInfoSet();
        Collections.sort(routes, (r1, r2) -> {
            int rc = r1.getUrl().compareToIgnoreCase(r2.getUrl());
            return rc != 0 ? rc : r1.getMethod().compareTo(r2.getMethod());
        });

        ListRoutesResponse.Builder responseb = ListRoutesResponse.newBuilder();
        responseb.addAllRoutes(routes);
        observer.complete(responseb.build());
    }
}
