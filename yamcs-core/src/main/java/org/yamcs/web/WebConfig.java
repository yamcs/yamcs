package org.yamcs.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfig;

/**
 * Data holder for webConfig section of yamcs.yamnl
 */
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);
    private static WebConfig INSTANCE;

    private int port;
    private List<String> webRoots = new ArrayList<>(2);
    private boolean zeroCopyEnabled = true;

    // Refer to W3C spec for understanding these properties
    // Cross-origin Resource Sharing (CORS) enables ajaxified use of the REST api by
    // remote web applications.
    private CorsConfig corsConfig;

    private WebConfig() {
        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");

        if (yconf.containsKey("webPort")) {
            log.warn("Property 'webPort' in yamcs.yaml is deprecated. Instead nest new property 'port' under 'webConfig'.");
            port = yconf.getInt("webPort");
        }
        if (yconf.containsKey("webRoot")) {
            log.warn("Property 'webRoot' in yamcs.yaml is deprecated. Instead nest 'webRoot' under 'webConfig'.");
            if (yconf.isList("webRoot")) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                List<String> rootConf = (List) yconf.getList("webRoot");
                for (String root : rootConf) {
                    webRoots.add(root);
                }
            } else {
                webRoots.add(yconf.getString("webRoot"));
            }
        }
        if (yconf.containsKey("zeroCopyEnabled")) {
            log.warn("Property 'zeroCopyEnabled' in yamcs.yaml is deprecated. Instead nest 'zeroCopyEnabled' under 'webConfig'.");
            zeroCopyEnabled = yconf.getBoolean("zeroCopyEnabled");
        }

        CorsConfig.Builder corsb = null;
        if (yconf.containsKey("webConfig")) {
            Map<String, Object> webConfig = yconf.getMap("webConfig");

            port = YConfiguration.getInt(webConfig, "port", 8090);
            zeroCopyEnabled = YConfiguration.getBoolean(webConfig, "zeroCopyEnabled", zeroCopyEnabled);

            if (webConfig.containsKey("webRoot")) {
                if (YConfiguration.isList(webConfig, "webRoot")) {
                    List<String> rootConf = YConfiguration.getList(webConfig, "webRoot");
                    webRoots.addAll(rootConf);
                } else {
                    webRoots.add(YConfiguration.getString(webConfig, "webRoot"));
                }
            }
            if(webConfig.containsKey("cors")) {
                Map<String, Object> ycors = YConfiguration.getMap(webConfig, "cors");
                if (YConfiguration.getBoolean(ycors, "enabled")) {
                    if (YConfiguration.isList(ycors, "allowOrigin")) {
                        List<String> originConf = YConfiguration.getList(ycors, "allowOrigin");
                        corsb = CorsConfig.withOrigins(originConf.toArray(new String[originConf.size()]));
                    } else {
                        corsb = CorsConfig.withOrigin(YConfiguration.getString(ycors, "allowOrigin"));
                    }
                    if (YConfiguration.getBoolean(ycors, "allowCredentials")) {
                        corsb.allowCredentials();
                    }
                }
            }
        } else {
            // Allow CORS requests for unprotected Yamcs instances
            // (Browsers would anyway strip Authorization header)
            corsb = CorsConfig.withAnyOrigin();
        }

        if (corsb != null) {
            corsb.allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE);
            corsb.allowedRequestHeaders(Names.CONTENT_TYPE, Names.ACCEPT, Names.AUTHORIZATION, Names.ORIGIN);
            corsConfig = corsb.build();
        }
    }

    public static synchronized WebConfig getInstance() {
        if (INSTANCE != null) return INSTANCE;
        INSTANCE = new WebConfig();
        return INSTANCE;
    }

    public int getPort() {
        return port;
    }

    public boolean isZeroCopyEnabled() {
        return zeroCopyEnabled;
    }

    public List<String> getWebRoots() {
        return webRoots;
    }

    public CorsConfig getCorsConfig() {
        return corsConfig;
    }
}
