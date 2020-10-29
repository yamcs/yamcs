package org.yamcs.yarch.streamsql;

import java.util.Iterator;

import org.yamcs.yarch.Tuple;

public interface StreamSqlResult extends Iterator<Tuple> {

   /**
    * Close the associated resources
    */
   void close();
}
