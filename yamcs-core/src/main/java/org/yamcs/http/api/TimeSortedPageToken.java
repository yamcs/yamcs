package org.yamcs.http.api;

import java.util.Base64;

import com.google.gson.Gson;

/**
 * Stateless continuation token for paged requests that output timesorted data
 */
public class TimeSortedPageToken {

    /**
     * Time associated with the last object that was emitted.
     * <p>
     * Consuming routes should not assume that an object with this time still exists, and rather use it for offsetting.
     */
    public long time;

    public TimeSortedPageToken(long time) {
        this.time = time;
    }

    public static TimeSortedPageToken decode(String encoded) {
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        return new Gson().fromJson(decoded, TimeSortedPageToken.class);
    }

    public String encodeAsString() {
        String json = new Gson().toJson(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
