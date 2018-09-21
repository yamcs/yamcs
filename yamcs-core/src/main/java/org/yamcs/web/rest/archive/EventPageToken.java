package org.yamcs.web.rest.archive;

import java.util.Base64;

import com.google.gson.Gson;

/**
 * Stateless continuation token for paged requests on the event table
 */
public class EventPageToken {

    public long gentime;
    public String source;
    public int seqNum;

    public EventPageToken(long gentime, String source, int seqNum) {
        this.gentime = gentime;
        this.source = source;
        this.seqNum = seqNum;
    }

    public static EventPageToken decode(String encoded) {
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        return new Gson().fromJson(decoded, EventPageToken.class);
    }

    public String encodeAsString() {
        String json = new Gson().toJson(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
