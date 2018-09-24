package org.yamcs.web.rest.archive;

import java.util.Base64;

import com.google.gson.Gson;

/**
 * Stateless continuation token for paged requests on the tm table
 */
public class PacketPageToken {

    public long gentime;
    public int seqNum;

    public PacketPageToken(long gentime, int seqNum) {
        this.gentime = gentime;
        this.seqNum = seqNum;
    }

    public static PacketPageToken decode(String encoded) {
        String decoded = new String(Base64.getUrlDecoder().decode(encoded));
        return new Gson().fromJson(decoded, PacketPageToken.class);
    }

    public String encodeAsString() {
        String json = new Gson().toJson(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
