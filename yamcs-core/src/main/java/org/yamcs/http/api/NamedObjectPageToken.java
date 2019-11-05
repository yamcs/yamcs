package org.yamcs.http.api;

import java.util.Base64;

import com.google.gson.Gson;

/**
 * Stateless continuation token for paged MDB requests.
 */
public class NamedObjectPageToken {

    /**
     * Qualified name of the last object that was emitted.
     * <p>
     * Consuming routes should not assume that this name still exists, and rather do an alphabetic comparison (this also
     * implies that the endpoint should return results in alphabetic order.
     */
    public String name;

    /**
     * Whether this represents a space system. Pagination is based on an ordering where space systems are returned
     * before the actual items.
     */
    public boolean spaceSystem;

    public NamedObjectPageToken(String name, boolean spaceSystem) {
        this.name = name;
        this.spaceSystem = spaceSystem;
    }

    public static NamedObjectPageToken decode(String encoded) {
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        return new Gson().fromJson(decoded, NamedObjectPageToken.class);
    }

    public String encodeAsString() {
        String json = new Gson().toJson(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
