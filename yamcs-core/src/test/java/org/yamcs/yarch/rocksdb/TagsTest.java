package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.junit.Test;
import org.yamcs.TimeInterval;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.YarchTestCase;

public class TagsTest extends YarchTestCase {

    private ArrayList<ArchiveTag> tags;
    private RdbTagDb tagDb;
    
    @Test
    public void testTags() throws Exception {
        final int n=1000;
        tags=new ArrayList<ArchiveTag>(n+4);
        String path = "/tmp/test_rdbtags";
        FileUtils.deleteRecursively(new File(path).toPath());
        tagDb = new RdbTagDb(path);
        
        //insert two tags without stop
        insertTag(ArchiveTag.newBuilder().setName("plusinfinity1").setStart(1000).build());
        insertTag(ArchiveTag.newBuilder().setName("plusinfinity2").setStart(2000).build());

        for(int i=n;i>0;i--) {
            insertTag(ArchiveTag.newBuilder().setName("tag"+i).setStart(i*1000).setStop(i*1500).build());
        }
        insertTag(ArchiveTag.newBuilder().setName("minusinfinity1").setStop(1000).build());//n+3
        insertTag(ArchiveTag.newBuilder().setName("minusinfinity2").setStop(2000).build());//n+4
        Collections.sort(tags, new ArchiveTagComparator());
        
        checkRetrieval(-1, -1);

        checkRetrieval(-1, n/2);
        checkRetrieval(n/2,-1);
        checkRetrieval(n/3,2*n/3);
        //remove minusinfinity1
        ArchiveTag t = tags.get(findId("minusinfinity1"));
        ArchiveTag rtag=tagDb.deleteTag(t.hasStart()?t.getStart():0, t.getId());
        checkTag(tags.get(0), rtag);
        tags.remove(0);
        checkRetrieval(-1,1);
        //remove plusinfinity2
        t = tags.get(findId("plusinfinity2"));
        rtag=tagDb.deleteTag(t.hasStart()?t.getStart():0, t.getId());
        checkTag(tags.get(3), rtag);
        tags.remove(3);
        checkRetrieval(-1,-1);
        //remove n/3 from the others
        for(int i=3;i<3+n/3;i++) {
            ArchiveTag tag=tags.get(3);
            rtag=tagDb.deleteTag(tag.hasStart()?tag.getStart():0, tag.getId());
            checkTag(tag, rtag);
            tags.remove(3);
        }
        checkRetrieval(-1,-1);
        //change plusinfinity1
        ArchiveTag ntag=ArchiveTag.newBuilder().setName("plusinfinity1changed").setStart(20000).setId(1).build();
        ArchiveTag otag=tags.get(findId("plusinfinity1"));
        rtag=tagDb.updateTag(otag.hasStart()?otag.getStart():0, otag.getId(), ntag);
        checkTag(ntag,rtag);
        tags.remove(findId("plusinfinity1"));
        tags.add(rtag);
        Collections.sort(tags, new ArchiveTagComparator());
        checkRetrieval(-1,-1);
        
        //change minusinfinity2
        ntag=ArchiveTag.newBuilder().setName("minunsinfinity2changed").setStart(40000).setId(n+4).build();
        otag=tags.get(findId("minusinfinity2"));
        rtag=tagDb.updateTag(otag.hasStart()?otag.getStart():0, otag.getId(), ntag);
        checkTag(ntag,rtag);
        tags.remove(findId("minusinfinity2"));
        tags.add(rtag);
        Collections.sort(tags, new ArchiveTagComparator());
        checkRetrieval(-1,-1);
        int m=tags.size();
        checkRetrieval(-1, m/3);
        checkRetrieval(m/3,-1);
        checkRetrieval(m/4,2*m/4);
    }

    private int findId(String name){
        for(int i=0;i<tags.size();i++) {
            if(name.equals(tags.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }
    private ArchiveTag insertTag(ArchiveTag tag) throws IOException {
        ArchiveTag rtag=tagDb.insertTag(tag);
        checkTag(tag, rtag);
        assertTrue(rtag.hasId());
        tags.add(rtag);
        assertEquals(tags.size(), rtag.getId());
        return rtag;
    }
    
    private void checkRetrieval(int k1, int k2) throws IOException {
        TimeInterval intv = new TimeInterval();
        long start=Long.MIN_VALUE,stop=Long.MAX_VALUE;
        if(k1!=-1) {
            start=tags.get(k1).getStart();
            intv.setStart(tags.get(k1).getStart());
        }
        
        if(k2!=-1){
            stop=tags.get(k2).getStop();
            intv.setStop(tags.get(k2).getStop());
        }
        
        final long lo = start;
        final long hi = stop;
        tagDb.getTags(intv, new TagReceiver() {
            
            int i=0;

            @Override
            public void onTag(ArchiveTag tag) {
                while (i<tags.size()) {
                    if(tags.get(i).hasStop() && tags.get(i).getStop()<lo) {
                        i++;
                        continue;
                    }
                    if(tags.get(i).hasStart() && tags.get(i).getStart()>hi) {
                        i++;
                        continue;
                    }
                    break;
                }
                
                assertTrue(tag.hasId());
                checkTag(tags.get(i), tag);
                i++;
            }

            @Override
            public void finished() {}
        });
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
