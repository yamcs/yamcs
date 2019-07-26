package org.yamcs.http.api.archive;

import java.util.Base64;

import com.google.gson.Gson;
import com.google.protobuf.Timestamp;

/**
 * Stateless continuation token for paged requests on the tm table
 */
public class PacketPageToken {

    public Timestamp gentime;
    public int seqNum;

    public PacketPageToken(Timestamp timestamp, int seqNum) {
        this.gentime = timestamp;
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
