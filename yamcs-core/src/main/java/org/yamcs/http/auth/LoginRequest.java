package org.yamcs.http.auth;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.HandlerContext;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.UsernamePasswordToken;

public class LoginRequest extends FormData {

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String CLIENT_ID = "client_id";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String STATE = "state";

    public LoginRequest(HandlerContext ctx) throws BadRequestException {
        super(ctx);
        requireParameters(USERNAME, PASSWORD, CLIENT_ID, REDIRECT_URI);
        acceptParameter(STATE);
    }

    public String getRedirectURI() {
        return parameters.get(REDIRECT_URI);
    }

    public String getUsername() {
        return parameters.get(USERNAME);
    }

    public String getClientID() {
        return parameters.get(CLIENT_ID);
    }

    public AuthenticationToken getUsernamePasswordToken() {
        return new UsernamePasswordToken(parameters.get(USERNAME), parameters.get(PASSWORD).toCharArray());
    }

    public String getState() {
        return parameters.get(STATE);
    }
}
