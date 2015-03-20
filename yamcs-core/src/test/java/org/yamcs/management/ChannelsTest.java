package org.yamcs.management;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.Channel;
import org.yamcs.ChannelClient;
import org.yamcs.ChannelException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.ui.ChannelControlClient;
import org.yamcs.ui.ChannelListener;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.YamcsManagement.ChannelInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.Statistics;

import static org.junit.Assert.*;


public class ChannelsTest {
   static EmbeddedHornetQ hornetServer;
    @BeforeClass
    public static void setupHornetAndManagement() throws Exception {
        YConfiguration.setup("ChannelsTest");
        hornetServer=YamcsServer.setupHornet();
        ManagementService.setup(true,true);
    }
    
    @AfterClass
    public static void afterClass() throws Exception {
        ManagementService.getInstance().shutdown();
	YamcsServer.stopHornet();
    }
    
    @Test
    public void createChannelWithoutClient() throws Exception {
        YamcsConnector yconnector=new YamcsConnector();
        ChannelControlClient ccc=new ChannelControlClient(yconnector);
        ccc.setChannelListener(new MyListener("ChannelsTest"));
        yconnector.connect(YamcsConnectData.parse("yamcs:///")).get(5,TimeUnit.SECONDS);

        try {
            ccc.createChannel("ChannelsTest0", "test1", "dummy", "test", false, new int[]{10,14});
            assertTrue("YamcsException was expected", false);
        } catch(YamcsException e) {
            assertEquals("createChannel invoked with a list full of invalid client ids", e.getMessage());
        }
        ccc.close();
        yconnector.disconnect();
    }

    @Test
    public void createAndSwitchChannel() throws Exception {
        YamcsConnector yconnector=new YamcsConnector();
        ChannelControlClient ccc=new ChannelControlClient(yconnector);
        MyListener ml=new MyListener("ChannelsTest1");
        ccc.setChannelListener(ml);
        Future<String> f=yconnector.connect(YamcsConnectData.parse("yamcs:///"));
        f.get(5, TimeUnit.SECONDS);
        
        ccc.createChannel("ChannelsTest1", "channel1", "dummy", "", true, new int[0]);
        
        MyChannelClient client=new MyChannelClient();
        Channel chan=Channel.getInstance("ChannelsTest1", "channel1");
        assertNotNull(chan);
        
        chan.connect(client);
        client.channel=chan;
        
        ManagementService.getInstance().registerClient("ChannelsTest1", "channel1", client);
        
        ccc.createChannel("ChannelsTest1", "channel2", "dummy", "", false, new int[]{1});
        
        Thread.sleep(3000); //to make sure that this event will not overwrite the previous ChannelsTest1,channel1 one 
        ccc.connectToChannel("ChannelsTest1", "channel1", new int[]{1}); //this one should trigger the closing of non permanent channel2 because no more client connected
        chan.disconnect(client);
        ManagementService.getInstance().unregisterClient(1);
        chan.quit();
        
        Thread.sleep(3000);//to allow for events to come
        ccc.close();
        yconnector.disconnect();
        
        assertEquals(2, ml.channelUpdated.size());
        ChannelInfo ci=ml.channelUpdated.get("channel1");
        assertEquals("ChannelsTest1",ci.getInstance());
        assertEquals("channel1",ci.getName());
        assertEquals("dummy",ci.getType());
        assertEquals(ServiceState.RUNNING, ci.getState());
        
        ci=ml.channelUpdated.get("channel2");
        assertEquals("ChannelsTest1",ci.getInstance());
        assertEquals("channel2",ci.getName());
        assertEquals("dummy",ci.getType());
        assertEquals("",ci.getSpec());
        assertEquals(ServiceState.RUNNING, ci.getState());
        
        
        assertEquals(2, ml.channelClosedList.size());
        ci=ml.channelClosedList.get(0);
        assertEquals("ChannelsTest1",ci.getInstance());
        assertEquals("channel2",ci.getName());
        
        ci=ml.channelClosedList.get(1);
        assertEquals("ChannelsTest1",ci.getInstance());
        assertEquals("channel1",ci.getName());
        
        
        assertEquals(3, ml.clientUpdatedList.size());
        
        ClientInfo cli=ml.clientUpdatedList.get(0);
        assertEquals("ChannelsTest1",cli.getInstance());
        assertEquals("channel1",cli.getChannelName());
        assertEquals(1,cli.getId());
        assertEquals("random-test-user",cli.getUsername());
        assertEquals("random-app-name",cli.getApplicationName());
        
        cli=ml.clientUpdatedList.get(1);
        assertEquals("ChannelsTest1",cli.getInstance());
        assertEquals("channel2",cli.getChannelName());
        assertEquals(1,cli.getId());
        
        cli=ml.clientUpdatedList.get(2);
        assertEquals("ChannelsTest1",cli.getInstance());
        assertEquals("channel1",cli.getChannelName());
        assertEquals(1,cli.getId());
        
        assertEquals(1,ml.clientDisconnectedList.size());
        cli=ml.clientDisconnectedList.get(0);
        assertEquals("ChannelsTest1",cli.getInstance());
        assertEquals("channel1",cli.getChannelName());
        assertEquals(1,cli.getId());
       
    }
    
    static class MyListener implements ChannelListener {
        Map<String, ChannelInfo> channelUpdated=Collections.synchronizedMap(new HashMap<String,ChannelInfo>());
        List<ChannelInfo> channelClosedList=Collections.synchronizedList(new ArrayList<ChannelInfo>());
        List<ClientInfo> clientUpdatedList=Collections.synchronizedList(new ArrayList<ClientInfo>());
        List<ClientInfo> clientDisconnectedList=Collections.synchronizedList(new ArrayList<ClientInfo>());
        String instance;
        
        public MyListener(String instance) {
            this.instance=instance;
        }
        
        
        @Override
        public void log(String text) {
           System.out.println("log: "+text);
            
        }

        @Override
        public void popup(String text) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void channelUpdated(ChannelInfo ci) {
            if(instance.equals(ci.getInstance())) {
                channelUpdated.put(ci.getName(), ci);
            }
        }

        @Override
        public void channelClosed(ChannelInfo ci) {
            if(instance.equals(ci.getInstance()))
                channelClosedList.add(ci);
        }

        @Override
        public void clientUpdated(ClientInfo ci) {
            if(instance.equals(ci.getInstance())) {
                clientUpdatedList.add(ci);
            }
        }

        @Override
        public void clientDisconnected(ClientInfo ci) {
            if(instance.equals(ci.getInstance())) {
                clientDisconnectedList.add(ci);
            }
        }

        @Override
        public void updateStatistics(Statistics s) {
            // TODO Auto-generated method stub
            
        }
    }

    static class MyChannelClient implements ChannelClient {
        Channel channel;

        @Override
        public void switchChannel(Channel c) throws ChannelException {
            channel.disconnect(this);
            c.connect(this);
            channel=c;
        }

        @Override
        public void channelQuit() {
            // TODO Auto-generated method stub
            
        }

        @Override
        public String getUsername() {
            return "random-test-user";
        }

        @Override
        public String getApplicationName() {
            return "random-app-name";
        }
/*
        @Override
        public void connectToChannel(Channel c) throws ChannelException {
            this.channel=c;
            c.connect(this);
        }
  */      
    }
}