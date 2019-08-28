package org.yamcs.security;

/**
 * Credentials for identifying as an application, for example the singleton application that represents a service
 * account.
 */
public class ApplicationCredentials implements AuthenticationToken {

    private final String applicationId;
    private final String applicationSecret;

    private String become;

    public ApplicationCredentials(String applicationId, String applicationSecret) {
        this.applicationId = applicationId;
        this.applicationSecret = applicationSecret;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationSecret() {
        return applicationSecret;
    }

    public void setBecome(String become) {
        this.become = become;
    }

    public String getBecome() {
        return become;
    }
}
