package org.yamcs.tctm;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * Object that is used to persist link state information across Yamcs restarts.
 */
public class LinkMemento {

    @SerializedName("links")
    private Map<String, LinkState> links = new HashMap<>();

    public void addLinkState(String link, LinkState state) {
        links.put(link, state);
    }

    public LinkState getLinkState(String link) {
        return links.get(link);
    }
}
