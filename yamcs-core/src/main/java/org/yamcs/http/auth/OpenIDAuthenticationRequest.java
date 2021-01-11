package org.yamcs.http.auth;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.HandlerContext;

/**
 * An Authentication Request is an OAuth 2.0 Authorization Request that requests that the End-User be authenticated by
 * the Authorization Server.
 * <p>
 * Authorization Servers MUST support the use of the HTTP GET and POST methods
 */
public class OpenIDAuthenticationRequest extends FormData {

    /**
     * REQUIRED. OpenID Connect requests MUST contain the openid scope value. If the openid scope value is not present,
     * the behavior is entirely unspecified. Other scope values MAY be present. Scope values used that are not
     * understood by an implementation SHOULD be ignored
     */
    private static final String SCOPE = "scope";

    /**
     * REQUIRED. OAuth 2.0 Response Type value that determines the authorization processing flow to be used, including
     * what parameters are returned from the endpoints used. When using the Authorization Code Flow, this value is code.
     */
    private static final String RESPONSE_TYPE = "response_type";

    /**
     * REQUIRED. OAuth 2.0 Client Identifier valid at the Authorization Server.
     */
    private static final String CLIENT_ID = "client_id";

    /**
     * REQUIRED. Redirection URI to which the response will be sent. This URI MUST exactly match one of the Redirection
     * URI values for the Client pre-registered at the OpenID Provider.
     */
    private static final String REDIRECT_URI = "redirect_uri";

    /**
     * RECOMMENDED. Opaque value used to maintain state between the request and the callback. Typically, Cross-Site
     * Request Forgery (CSRF, XSRF) mitigation is done by cryptographically binding the value of this parameter with a
     * browser cookie.
     */
    private static final String STATE = "state";

    public OpenIDAuthenticationRequest(HandlerContext ctx) throws BadRequestException {
        super(ctx);
        requireParameters(SCOPE, RESPONSE_TYPE, CLIENT_ID, REDIRECT_URI);
        acceptParameter(STATE);
    }

    public String getScope() {
        return parameters.get(SCOPE);
    }

    public String getResponseType() {
        return parameters.get(RESPONSE_TYPE);
    }

    public String getClientID() {
        return parameters.get(CLIENT_ID);
    }

    public String getRedirectURI() {
        return parameters.get(REDIRECT_URI);
    }

    public String getState() {
        return parameters.get(STATE);
    }
}
