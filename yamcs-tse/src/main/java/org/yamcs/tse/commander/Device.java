package org.yamcs.tse.commander;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

public abstract class Device {

    private static final Logger log = LoggerFactory.getLogger(Device.class);

    protected String id;
    protected String locator;
    protected String description;

    protected String responseTermination;
    protected int responseTimeout = 3000;

    protected Charset encoding = StandardCharsets.US_ASCII;

    public Device(String id, Map<String, Object> args) {
        this.id = id;
        locator = YConfiguration.getString(args, "locator");

        if (args.containsKey("description")) {
            description = YConfiguration.getString(args, "description");
        }
        if (args.containsKey("responseTermination")) {
            responseTermination = YConfiguration.getString(args, "responseTermination");
        }
        if (args.containsKey("responseTimeout")) {
            responseTimeout = YConfiguration.getInt(args, "responseTimeout");
        }
    }

    public String getId() {
        return id;
    }

    public String getLocator() {
        return locator;
    }

    public String getDescription() {
        return description;
    }

    public String getResponseTermination() {
        return responseTermination;
    }

    public int getResponseTimeout() {
        return responseTimeout;
    }

    public String command(String cmd) throws IOException, TimeoutException {
        log.info("{} <<< {}", id, cmd);
        write(cmd);
        if (cmd.contains("?") || cmd.contains("!")) { // Should maybe make this configurable
            String response = read();
            if (response != null) {
                log.info("{} >>> {}", id, response);
            }
            return response;
        }
        return null;
    }

    public abstract void connect() throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract void write(String cmd) throws IOException;

    public abstract String read() throws IOException, TimeoutException;
}
