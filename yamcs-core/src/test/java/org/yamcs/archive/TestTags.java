package org.yamcs.archive;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.YamcsServer;
import org.yamcs.archive.IndexServer;
import org.yamcs.archive.TmProviderAdapter;

import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.DeleteTagRequest;
import org.yamcs.protobuf.Yamcs.TagRequest;
import org.yamcs.protobuf.Yamcs.TagResult;
import org.yamcs.protobuf.Yamcs.UpsertTagRequest;
import org.yamcs.yarch.YarchTestCase;

public class TestTags extends YarchTestCase {
    static EmbeddedHornetQ hornetServer;

    YamcsClient msgClient;
    SimpleString indexAddress;
    private ArrayList<ArchiveTag> tags;
    private String instance;
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        hornetServer=YamcsServer.setupHornet();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        hornetServer.stop();
    }
 
    @Test
    public void testTags() throws Exception {
        final int n=1000;
        tags=new ArrayList<ArchiveTag>(n+4);
        instance=context.getDbName();
        execute("create stream tm_realtime "+TmProviderAdapter.TM_TUPLE_DEFINITION.getStringDefinition());
        execute("create stream tm_dump "+TmProviderAdapter.TM_TUPLE_DEFINITION.getStringDefinition());

        IndexServer is=new IndexServer(instance);
        new Thread(is).start();
        
        YamcsSession ys=YamcsSession.newBuilder().build();
        msgClient=ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        indexAddress=Protocol.getYarchIndexControlAddress(instance);
        
        
        //insert two tags without stop
        insertTag(ArchiveTag.newBuilder().setName("plusinfinity1").setStart(1000).build());
        insertTag(ArchiveTag.newBuilder().setName("plusinfinity2").setStart(2000).build());

        for(int i=n;i>0;i--) {
            insertTag(ArchiveTag.newBuilder().setName("tag"+i).setStart(i*1000).setStop(i*1500).build());
        }
        insertTag(ArchiveTag.newBuilder().setName("minusinfinity1").setStop(1000).build());//n+3
        insertTag(ArchiveTag.newBuilder().setName("minusinfinity2").setStop(2000).build());//n+4
        Collections.sort(tags, new ArchiveTagComparator());
        
        Thread.sleep(1000);//wait to finish the indexing
        checkRetrieval(-1, -1);

        checkRetrieval(-1, n/2);
        checkRetrieval(n/2,-1);
        checkRetrieval(n/3,2*n/3);
        
        //remove minusinfinity1
        ArchiveTag rtag=(ArchiveTag)msgClient.executeRpc(indexAddress, "deleteTag", 
                DeleteTagRequest.newBuilder().setTag(tags.get(findId("minusinfinity1"))).build(), ArchiveTag.newBuilder());
        checkTag(tags.get(0), rtag);
        tags.remove(0);
        checkRetrieval(-1,1);
        //remove plusinfinity2
        rtag=(ArchiveTag)msgClient.executeRpc(indexAddress, "deleteTag", 
                DeleteTagRequest.newBuilder().setTag(tags.get(findId("plusinfinity2"))).build(), ArchiveTag.newBuilder());
        checkTag(tags.get(3), rtag);
        tags.remove(3);
        checkRetrieval(-1,-1);
        //remove n/3 from the others
        for(int i=3;i<3+n/3;i++) {
            ArchiveTag tag=tags.get(3);
            rtag=(ArchiveTag)msgClient.executeRpc(indexAddress, "deleteTag", 
                    DeleteTagRequest.newBuilder().setTag(tag).build(), ArchiveTag.newBuilder());
            checkTag(tag, rtag);
            tags.remove(3);
        }
        checkRetrieval(-1,-1);
        
        //change plusinfinity1
        ArchiveTag ntag=ArchiveTag.newBuilder().setName("plusinfinity1changed").setStart(20000).setId(1).build();
        rtag=(ArchiveTag)msgClient.executeRpc(indexAddress, "upsertTag", 
                UpsertTagRequest.newBuilder().setOldTag(tags.get(findId("plusinfinity1"))).setNewTag(ntag).build(), ArchiveTag.newBuilder());
        checkTag(ntag,rtag);
        tags.remove(findId("plusinfinity1"));
        tags.add(rtag);
        Collections.sort(tags, new ArchiveTagComparator());
        checkRetrieval(-1,-1);
        
        //change minusinfinity2
        ntag=ArchiveTag.newBuilder().setName("minunsinfinity2changed").setStart(40000).setId(n+4).build();
        rtag=(ArchiveTag)msgClient.executeRpc(indexAddress, "upsertTag", 
                UpsertTagRequest.newBuilder().setOldTag(tags.get(findId("minusinfinity2"))).setNewTag(ntag).build(), ArchiveTag.newBuilder());
        checkTag(ntag,rtag);
        tags.remove(findId("minusinfinity2"));
        tags.add(rtag);
        Collections.sort(tags, new ArchiveTagComparator());
        checkRetrieval(-1,-1);

        int m=tags.size();
        checkRetrieval(-1, m/3);
        checkRetrieval(m/3,-1);
        checkRetrieval(m/4,2*m/4);
        
        msgClient.close();

        is.quit();
        ys.close();
        
    }

    private int findId(String name){
        for(int i=0;i<tags.size();i++) {
            if(name.equals(tags.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }
    private ArchiveTag insertTag(ArchiveTag tag) throws YamcsApiException, HornetQException, YamcsException {
        ArchiveTag rtag=(ArchiveTag)msgClient.executeRpc(indexAddress, "upsertTag",
                UpsertTagRequest.newBuilder().setNewTag(tag).build(),ArchiveTag.newBuilder());
        checkTag(tag, rtag);
        assertTrue(rtag.hasId());
        tags.add(rtag);
        assertEquals(tags.size(), rtag.getId());
        return rtag;
    }
    
    private void checkRetrieval(int k1, int k2) throws YamcsApiException, HornetQException, YamcsException {
        TagRequest.Builder trb=TagRequest.newBuilder().setInstance(instance);
        long start=Long.MIN_VALUE,stop=Long.MAX_VALUE;
        if(k1!=-1) {
            start=tags.get(k1).getStart();
            trb.setStart(tags.get(k1).getStart());
        }
        
        if(k2!=-1){
            stop=tags.get(k2).getStop();
            trb.setStop(tags.get(k2).getStop());
        }
        msgClient.sendRequest(indexAddress, "getTag", trb.build());
        int i=0,k=0;
        TagResult result=null;
        while(i<tags.size()) {
            if(tags.get(i).hasStop() && tags.get(i).getStop()<start) {
                i++;
                continue;
            }
            if(tags.get(i).hasStart() && tags.get(i).getStart()>stop) {
                i++;
                continue;
            }
            if(result==null) {
                result=(TagResult)msgClient.receiveData(TagResult.newBuilder());
                assertNotNull(result);
                
                assertEquals(instance,result.getInstance());
                assertTrue(result.getTagCount()>0);
                k=0;
            }
            ArchiveTag rtag=result.getTag(k);
            assertTrue(rtag.hasId());
            checkTag(tags.get(i), rtag);
            k++;
            if(k==result.getTagCount()) result=null;
            i++;
        }
        TagResult res=(TagResult)msgClient.receiveData(TagResult.newBuilder());
        assertNull(res);
        
    }

    private void checkTag(ArchiveTag tag, ArchiveTag rtag) {
        if(tag.hasStart()) {
            assertTrue(rtag.hasStart());
            assertEquals(tag.getStart(), rtag.getStart());
        } else {
            assertFalse(rtag.hasStart());
        }
        if(tag.hasStop()) {
            assertTrue(rtag.hasStop());
            assertEquals(tag.getStop(), rtag.getStop());
        } else {
            assertFalse(rtag.hasStop());
        }
        assertEquals(tag.getName(), rtag.getName());
        if(tag.hasDescription())assertEquals(tag.getDescription(), rtag.getDescription());
        if(tag.hasColor())assertEquals(tag.getColor(), rtag.getColor());
    }
    
    
    static class ArchiveTagComparator implements Comparator<ArchiveTag> {

        @Override
        public int compare(ArchiveTag t1, ArchiveTag t2) {
            if(t1.hasStart()) {
                if(t2.hasStart()){
                    if(t1.getStart()==t2.getStart())
                        return Integer.valueOf(t1.getId()).compareTo(t2.getId());
                    else return Long.valueOf(t1.getStart()).compareTo(t2.getStart());
                } else {
                    return 1;
                }
            } else if(t2.hasStart()){
                return -1;
            } else {
                return Integer.valueOf(t1.getId()).compareTo(t2.getId());
            }
        }
        
    }
}
