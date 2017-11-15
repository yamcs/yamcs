package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.protobuf.YamcsManagement.ClientInfo;

public class ClientControlImpl extends StandardMBean implements ClientControl {
    ClientInfo clientInfo;

    public ClientControlImpl(ClientInfo clientInfo) throws NotCompliantMBeanException {
        super(ClientControl.class);
        this.clientInfo = clientInfo;
    }

    @Override
    public String getApplicationName() {
        return clientInfo.getApplicationName();
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }
}
