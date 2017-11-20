package org.yamcs.security;
import org.apache.activemq.artemis.api.core.client.ClientMessage;

/**
 * Created by msc on 05/05/15.
 */
public class HqClientMessageToken extends UsernamePasswordToken {

    public HqClientMessageToken(ClientMessage message, String password) {
        super( message.getStringProperty( ArtemisAuthInterceptor.USERNAME_PROPERTY ), password);
    }
}
