package org.yamcs.security;
import org.hornetq.api.core.client.ClientMessage;

/**
 * Created by msc on 05/05/15.
 */
public class HqClientMessageToken extends UsernamePasswordToken {

    public HqClientMessageToken(ClientMessage message, String password) {
        super( message.getStringProperty( HornetQAuthInterceptor.USERNAME_PROPERTY ), password);
    }
}
