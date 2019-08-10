package org.yamcs.security;

/**
 * Represents a token (or 'authorization_code' in oauth terms) issued by an <em>external</em> identity server. This is
 * used in situations where Yamcs does not itself perform the authentication.
 * <p>
 * The type of code is an implementation choice of the AuthModule. It may directly represent an externally issued token,
 * although ideally the AuthModule should not expose such information, and instead manage an internal mapping via
 * self-issued transient tokens.
 * <p>
 * See {@link SpnegoAuthModule} for a representative example
 */
public class ThirdPartyAuthorizationCode implements AuthenticationToken {

    private String code;

    public ThirdPartyAuthorizationCode(String code) {
        this.code = code;
    }

    public String getPrincipal() {
        return code;
    }
}
