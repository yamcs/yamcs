package org.yamcs.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;

/**
 * Data holder for webConfig section of yamcs.yaml
 */
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);
    private static WebConfig INSTANCE = new WebConfig();

    private int port;
    private List<String> webRoots = new ArrayList<>(2);
    private boolean zeroCopyEnabled = true;
    private int maxWsFrameLength = 65535;

    // Refer to W3C spec for understanding these properties
    // Cross-origin Resource Sharing (CORS) enables ajaxified use of the REST api by
    // remote web applications.
    private CorsConfig corsConfig;

    private List<GpbExtension> gpbExtensions = new ArrayList<>(0);

    // used for the websockets write buffer:
    // the higher the values, the more memory it might consume but it will be more resilient against unstable networks
    private WriteBufferWaterMark webSocketWriteBufferWaterMark;

    // Number of dropped messages after which to close the connection
    private int webSocketConnectionCloseNumDroppedMsg = 5;

    private WebConfig() {
        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");

        if (yconf.containsKey("webPort")) {
            log.warn(
                    "Property 'webPort' in yamcs.yaml is deprecated. Instead nest new property 'port' under 'webConfig'.");
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
            log.warn(
                    "Property 'zeroCopyEnabled' in yamcs.yaml is deprecated. Instead nest 'zeroCopyEnabled' under 'webConfig'.");
            zeroCopyEnabled = yconf.getBoolean("zeroCopyEnabled");
        }

        CorsConfigBuilder corsb = null;
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
            if (webConfig.containsKey("cors")) {
                Map<String, Object> ycors = YConfiguration.getMap(webConfig, "cors");
                if (YConfiguration.getBoolean(ycors, "enabled")) {
                    if (YConfiguration.isList(ycors, "allowOrigin")) {
                        List<String> originConf = YConfiguration.getList(ycors, "allowOrigin");
                        corsb = CorsConfigBuilder.forOrigins(originConf.toArray(new String[originConf.size()]));
                    } else {
                        corsb = CorsConfigBuilder.forOrigin(YConfiguration.getString(ycors, "allowOrigin"));
                    }
                    if (YConfiguration.getBoolean(ycors, "allowCredentials")) {
                        corsb.allowCredentials();
                    }
                }
            }

            if (webConfig.containsKey("gpbExtensions")) {
                List<Map<String, Object>> extensionsConf = YConfiguration.getList(webConfig, "gpbExtensions");
                for (Map<String, Object> conf : extensionsConf) {
                    GpbExtension extension = new GpbExtension();
                    extension.clazz = YConfiguration.getString(conf, "class");
                    extension.field = YConfiguration.getString(conf, "field");
                    gpbExtensions.add(extension);
                }

            }
        } else {
            // Allow CORS requests for unprotected Yamcs instances
            // (Browsers would anyway strip Authorization header)
            corsb = CorsConfigBuilder.forAnyOrigin();
        }

        if (corsb != null) {
            corsb.allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT,
                    HttpMethod.DELETE);
            corsb.allowedRequestHeaders(HttpHeaderNames.CONTENT_TYPE, HttpHeaderNames.ACCEPT,
                    HttpHeaderNames.AUTHORIZATION, HttpHeaderNames.ORIGIN);
            corsConfig = corsb.build();
        }
        if (yconf.containsKey("webConfig", "webSocket")) {
            Map<String, Object> ws = yconf.getMap("webConfig", "webSocket");
            if (ws.containsKey("writeBufferWaterMark")) {
                Map<String, Object> wswm = YConfiguration.getMap(ws, "writeBufferWaterMark");
                webSocketWriteBufferWaterMark = new WriteBufferWaterMark(YConfiguration.getInt(wswm, "low"),
                        YConfiguration.getInt(wswm, "high"));
            }
            webSocketConnectionCloseNumDroppedMsg = YConfiguration.getInt(ws, "connectionCloseNumDroppedMsg",
                    webSocketConnectionCloseNumDroppedMsg);
            if (webSocketConnectionCloseNumDroppedMsg < 1) {
                throw new ConfigurationException(
                        "Error in yamcs.yaml: webSocket->connectionCloseNumDroppedMsg has to be greater than 0. Provided value: "
                                + webSocketConnectionCloseNumDroppedMsg);
            }
            maxWsFrameLength = YConfiguration.getInt(ws, "maxFrameLength", maxWsFrameLength);
        }
        if (webSocketWriteBufferWaterMark == null) {
            webSocketWriteBufferWaterMark = new WriteBufferWaterMark(32 * 1024, 64 * 1024); // these are also default
                                                                                            // netty values
        }

    }

    public static WebConfig getInstance() {
        return INSTANCE;
    }

    public int getPort() {
        return port;
    }

    /**
     * Returns the write buffer water mark that shall be used for web sockets
     */
    public WriteBufferWaterMark getWebSocketWriteBufferWaterMark() {
        return webSocketWriteBufferWaterMark;
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

    public int getWebSocketConnectionCloseNumDroppedMsg() {
        return webSocketConnectionCloseNumDroppedMsg;
    }

    public List<GpbExtension> getGpbExtensions() {
        return gpbExtensions;
    }

    public int getWebSocketMaxFrameLength() {
        return maxWsFrameLength;
    }

    static class GpbExtension {
        String clazz;
        String field;
    }
}
