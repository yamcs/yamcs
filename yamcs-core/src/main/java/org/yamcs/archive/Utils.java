package org.yamcs.archive;

import java.util.Collection;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.YarchDatabase;

public class Utils {
    /**
     * close all table writers subscribed to any of the stream in the list
     * 
     * @param ydb
     * @param streamNames
     */
    static public void closeTableWriters(YarchDatabase ydb, Collection<String> streamNames) {
        for(String streamName: streamNames) {
            Stream s = ydb.getStream(streamName);
            if(s!=null) {
                for(StreamSubscriber ss:s.getSubscribers()) {
                    if(ss instanceof TableWriter) {
                        s.removeSubscriber(ss);
                        ((TableWriter)ss).close();
                    }
                }
            }
        }
    }
}
