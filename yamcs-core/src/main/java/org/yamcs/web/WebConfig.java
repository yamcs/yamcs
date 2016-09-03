package org.yamcs.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

/**
 * Data holder for webConfig section of yamcs.yamnl
 */
public class WebConfig {
    
    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);
    private static WebConfig INSTANCE;
    
    private int port;
    private List<String> webRoots = new ArrayList<>(2);
    private boolean zeroCopyEnabled = true;

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
        
        if (yconf.containsKey("webConfig")) {
            Map<String, Object> webConfig = yconf.getMap("webConfig");
            
            port = YConfiguration.getInt(webConfig, "webPort");
            zeroCopyEnabled = YConfiguration.getBoolean(webConfig, "zeroCopyEnabled", zeroCopyEnabled);
            
            if (webConfig.containsKey("webRoot")) {
                if (YConfiguration.isList(webConfig, "webRoot")) {
                    List<String> rootConf = YConfiguration.getList(webConfig, "webRoot");
                    for (String root : rootConf) {
                        webRoots.add(root);
                    }
                } else {
                    webRoots.add(yconf.getString("webRoot"));
                }
            }
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
}
