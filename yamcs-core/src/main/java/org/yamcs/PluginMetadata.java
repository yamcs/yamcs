package org.yamcs;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Date;
import java.util.Properties;

public class PluginMetadata {

    private String name;
    private String version;

    private String description;
    private String organization;
    private String organizationUrl;
    private Date lastUpdate;

    public PluginMetadata(Properties props) {
        name = requireNonNull(props.getProperty("name"));
        version = requireNonNull(props.getProperty("version"));

        description = props.getProperty("description");
        organization = props.getProperty("organization");
        organizationUrl = props.getProperty("organizationUrl");

        String timestamp = props.getProperty("generated");
        if (timestamp != null) {
            lastUpdate = Date.from(Instant.parse(timestamp));
        }
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getOrganization() {
        return organization;
    }

    public String getOrganizationUrl() {
        return organizationUrl;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }
}
