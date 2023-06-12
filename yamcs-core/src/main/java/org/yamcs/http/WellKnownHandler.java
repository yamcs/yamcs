package org.yamcs.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * RFC 8615 endpoint used by various protocols.
 */
public class WellKnownHandler extends HttpHandler {

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        String path = ctx.getPathWithoutContext();
        if (path.equals("/.well-known/oauth-authorization-server")) {
            handleOAuthMetadata(ctx);
            return;
        }

        throw new NotFoundException();
    }

    /**
     * Outputs metadata for OAuth 2.0 clients, including endpoints and capabalities.
     * 
     * @see RFC 8414 - OAuth 2.0 Authorization Server Metadata
     */
    private void handleOAuthMetadata(HandlerContext ctx) {
        ctx.requireGET();

        String issuerURL = ctx.getRequestBaseURL() + "/auth";
        JsonObject response = new JsonObject();
        response.addProperty("issuer", issuerURL);
        response.addProperty("authorization_endpoint", issuerURL + "/authorize");
        response.addProperty("token_endpoint", issuerURL + "/token");
        // response.addProperty("scopes_supported", new JsonArray());
        // response.addProperty("spnego_endpoint", issuerURL + "/spnego");

        JsonArray responseTypes = new JsonArray();
        // responseTypes.add("none");
        // responseTypes.add("code");
        responseTypes.add("token");
        // responseTypes.add("id_token");
        response.add("response_types_supported", responseTypes);

        JsonArray responseModes = new JsonArray();
        responseModes.add("query");
        response.add("response_modes_supported", responseModes);

        JsonArray grantTypes = new JsonArray();
        grantTypes.add("authorization_code");
        grantTypes.add("refresh_token");
        grantTypes.add("password");
        grantTypes.add("client_credentials");
        response.add("grant_types_supported", grantTypes);

        ctx.sendOK(response);
    }
}
