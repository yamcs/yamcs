package org.yamcs.security;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.auth.FormData;

public class OpenIDBackChannelLogoutRequest extends FormData {

    /**
     * REQUIRED. Logout Token from the OP for the RP identifying the End-User to be logged out.
     */
    private static final String LOGOUT_TOKEN = "logout_token";

    public OpenIDBackChannelLogoutRequest(HandlerContext ctx) throws BadRequestException {
        super(ctx);
        requireParameter(LOGOUT_TOKEN);
    }

    public String getLogoutToken() {
        return parameters.get(LOGOUT_TOKEN);
    }
}
