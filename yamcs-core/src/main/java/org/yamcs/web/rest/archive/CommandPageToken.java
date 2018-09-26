package org.yamcs.web.rest.archive;

import java.util.Base64;

import com.google.gson.Gson;

/**
 * Stateless continuation token for paged requests on the cmdhist table
 */
public class CommandPageToken {

    public long gentime;
    public String origin;
    public int seqNum;

    public CommandPageToken(long gentime, String origin, int seqNum) {
        this.gentime = gentime;
        this.origin = origin;
        this.seqNum = seqNum;
    }

    public static CommandPageToken decode(String encoded) {
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        return new Gson().fromJson(decoded, CommandPageToken.class);
    }

    public String encodeAsString() {
        String json = new Gson().toJson(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
