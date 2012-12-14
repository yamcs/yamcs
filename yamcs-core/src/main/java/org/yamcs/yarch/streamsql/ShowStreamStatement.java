package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.InputStream;
import org.yamcs.yarch.OutputStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.NotAStreamException;
import org.yamcs.yarch.streamsql.ResourceNotFoundException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class ShowStreamStatement extends StreamSqlStatement{

    String name;
    boolean showPort;

    public ShowStreamStatement(String name, boolean showPort) {
        this.name=name;
        this.showPort=showPort;
    }

    @Override
    public StreamSqlResult execute(ExecutionContext c) throws StreamSqlException {

        YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
        Stream s=null;
        synchronized(dict) {
            s=dict.getStream(name);
        }
        if(s==null) throw new ResourceNotFoundException(name);

        if(showPort) {
            final int port;
            if(s instanceof InputStream) {
                port=((InputStream)s).getPort();
            } else if(s instanceof OutputStream) {
                port=((OutputStream)s).getPort();
            } else {
                throw new NotAStreamException(name);
            }

            return new StreamSqlResult() {
                @Override
                public String toString() {
                    return "port="+port;
                };
            };
        } else {
            final String ret=s.toString();
            return new StreamSqlResult() {
                @Override
                public String toString() {
                    return ret;
                }
            };
        }
    }

}
