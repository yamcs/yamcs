package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.security.AuthModule;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.InvalidAuthenticationToken;
import org.yamcs.security.PrivilegeType;
import org.yamcs.security.User;
import org.yamcs.web.HttpServer;

import io.netty.handler.codec.http.HttpMethod;

public class AuthModuleTest {
    @BeforeClass
    public static void beforeClass() throws Exception {
        YConfiguration.setup("AuthModuleTest");
        new HttpServer().startServer();

        YamcsServer.setupYamcsServer();
    }

    @Test
    public void test1() throws Exception {
        YamcsConnectionProperties ycp = new YamcsConnectionProperties("localhost", 9190, "AuthModuleTest");
        RestClient restclient = new RestClient(ycp);
        CompletableFuture<byte[]> cf = restclient.doRequest("/instances", HttpMethod.GET);
        byte[] b = cf.get(3000, TimeUnit.MILLISECONDS);
        assertNotNull(b);
        YamcsInstances ys = YamcsInstances.newBuilder().mergeFrom(b).build();
        assertEquals(1, ys.getInstanceCount());
        assertEquals("AuthModuleTest", ys.getInstance(0).getName());
    }

    public static class MyAuthModule implements AuthModule {
        @Override
        public String[] getRoles(AuthenticationToken authenticationToken) throws InvalidAuthenticationToken {
            return null;
        }

        @Override
        public boolean hasRole(AuthenticationToken authenticationToken, String role) throws InvalidAuthenticationToken {
            return false;
        }

        @Override
        public boolean hasPrivilege(AuthenticationToken authenticationToken, PrivilegeType type, String privilege)
                throws InvalidAuthenticationToken {
            return false;
        }

        @Override
        public User getUser(AuthenticationToken authToken) {
            return null;
        }

        @Override
        public boolean verifyToken(AuthenticationToken authenticationToken) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public CompletableFuture<AuthenticationToken> authenticate(String type, Object authObject) {
            return null;
        }
    }

    static class MyAuthToken implements AuthenticationToken {

        @Override
        public String getPrincipal() {
            return "cuckoo";
        }
    }
}
