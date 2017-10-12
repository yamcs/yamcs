package org.yamcs.management;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.Processor;
import org.yamcs.ProcessorClient;
import org.yamcs.ProcessorException;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.TimeEncoding;

public class ClientControlImpl extends StandardMBean implements ClientControl {
    ClientInfo clientInfo;
    ProcessorClient client;

    ClientControlImpl(String yamcsInstance, int id, String username, String applicationName, String yprocName, ProcessorClient client) throws NotCompliantMBeanException {
        super(ClientControl.class);
        this.client=client;
        long loginTime = TimeEncoding.getWallclockTime();
        clientInfo=ClientInfo.newBuilder().setInstance(yamcsInstance)
                .setApplicationName(applicationName)
                .setProcessorName(yprocName).setUsername(username)
                .setLoginTime(loginTime)
                .setLoginTimeUTC(TimeEncoding.toString(loginTime))
                .setId(id).build();
    }

    @Override
    public String getApplicationName() {
        return clientInfo.getApplicationName();
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }


    public ProcessorClient getClient(){
        return client;
    }

    public void switchProcessor(Processor proc, AuthenticationToken authToken) throws ProcessorException {
        Processor oldProc = client.getProcessor();
        oldProc.disconnect(client);
        client.switchProcessor(proc, authToken);
        proc.connect(client);
        
        clientInfo=ClientInfo.newBuilder().mergeFrom(clientInfo)
                .setInstance(proc.getInstance()).setProcessorName(proc.getName())
                .build();
    }
}
