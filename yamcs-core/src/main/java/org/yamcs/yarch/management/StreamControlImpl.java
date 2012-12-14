package org.yamcs.yarch.management;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;


public class StreamControlImpl  extends StandardMBean implements StreamControl{
    Stream stream;
    StreamControlImpl(Stream stream) throws NotCompliantMBeanException {
        super(StreamControl.class);
        this.stream=stream;
    }
    @Override
    public String getName() {
        return stream.getName();
    }
    
    @Override
    public long getNumEmittedTuples() {
        return stream.getNumEmittedTuples();
    }
    @Override
    public String getType() {
       return stream.getClass().getName();
    }
    @Override
    public String getSchema() {
        return stream.getDefinition().getStringDefinition();
    }
    @Override
    public int getSubscriberCount() {
        return stream.getSubscriberCount();
    }
    @Override
    public List<String> getSubscribers() {
        List<String> sl=new ArrayList<String>();
        for(StreamSubscriber ss:stream.getSubscribers()) {
            sl.add(ss.toString());
        }
        return sl;
    }
    
    
}
