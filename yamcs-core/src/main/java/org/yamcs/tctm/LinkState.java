package org.yamcs.tctm;

import com.google.gson.annotations.SerializedName;

public class LinkState {

    @SerializedName("enabled")
    private boolean enabled;

    /**
     * Whether the link was enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Create state object for the given link.
     */
    public static LinkState forLink(Link link) {
        var state = new LinkState();
        state.enabled = !link.isDisabled();
        return state;
    }
}
