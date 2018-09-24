package org.yamcs.web.rest.mdb;

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

    public NamedObjectPageToken(String name) {
        this.name = name;
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
