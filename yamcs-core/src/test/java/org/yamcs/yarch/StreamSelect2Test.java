package org.yamcs.yarch;

import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.Test;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import static org.junit.Assert.*;

public class StreamSelect2Test extends YarchTestCase {
  StreamSqlResult res;
  final int n=1000000;

  public void createFeeder1() throws YarchException {
    AbstractStream s;
    YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
    final TupleDefinition tpdef=new TupleDefinition();
    tpdef.addColumn("x", DataType.INT);
    tpdef.addColumn("y", DataType.INT);
    
    s=(new AbstractStream(dict,"stream_in",tpdef) {
        @Override
        public void start() {
          for (int i=0;i<n;i++) {
            Integer x=i;
            Integer y=i*2;
            
            Tuple t=new Tuple(tpdef, new Object[]{x,y});
            emitTuple(t);
          }
        }

        @Override
        protected void doClose() {
        }
      });
      dict.addStream(s);
  }
  
  @Test
  public void testAdd() throws Exception {
    createFeeder1();
    
    res=execute("create stream stream_out1 as select x+y from stream_in");
    //res=execute("create output stream tm_out1 as select * from tm_in where id=3");
    YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
    Stream s=dict.getStream("stream_out1");
    final Semaphore finished=new Semaphore(0);
    s.addSubscriber(new StreamSubscriber() {
      @Override
      public void streamClosed(Stream stream) {
        
      }
      int k=0;
      @Override
      public void onTuple(Stream stream, Tuple tuple) {
        int xpy=(Integer)tuple.getColumn(0);
        assertEquals(3*k,xpy);
        k++;
        if(k==n) finished.release();
      }
    });
    s.start();
    finished.acquire();
    execute("close stream stream_in");
  }

  @Test
  public void testWindow1() throws Exception {
    createFeeder1();
    res=execute("CREATE STREAM stream_out1 AS SELECT SUM(y) from stream_in[SIZE 5 ADVANCE 5 ON x]");
    YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
    Stream s=dict.getStream("stream_out1");
    final Semaphore finished=new Semaphore(0);
   
    s.addSubscriber(new StreamSubscriber() {
      @Override
      public void streamClosed(Stream stream) { }
      int k=0;

      @Override
      public void onTuple(Stream stream, Tuple tuple) {
          // System.out.println("tuple: "+tuple);
        int sumy=(Integer)tuple.getColumn(0);
        assertEquals(2*(5*k+10),sumy);
        k+=5;
        
        if(k>=n-5) finished.release();
      }
    });
    //long t0=System.currentTimeMillis();
    s.start();
    finished.acquire();
    //System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
    execute("close stream stream_in");
    
  }

  @Test
  public void testWindow2() throws Exception {
      createFeeder1();
      res=execute("CREATE STREAM stream_out1 AS SELECT SUM(y+3) from stream_in[SIZE 5 ADVANCE 5 ON x]");
      YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
      Stream s=dict.getStream("stream_out1");
      final Semaphore finished=new Semaphore(0);
     
      s.addSubscriber(new StreamSubscriber() {
        @Override
        public void streamClosed(Stream stream) { }
        int k=0;

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            // System.out.println("tuple: "+tuple);
          int sumy=(Integer)tuple.getColumn(0);
          assertEquals(2*(5*k+10)+15,sumy);
          k+=5;
          
          if(k>=n-5) finished.release();
        }
      });
      //long t0=System.currentTimeMillis();
      s.start();
      finished.acquire();
      //System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
      execute("close stream stream_in");
    }
  
  @Test
  public void testWindow3() throws Exception {
      createFeeder1();
      res=execute("CREATE STREAM stream_out1 AS SELECT 2+SUM(x+y+1) from stream_in[SIZE 5 ADVANCE 5 ON x]");
      YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
      Stream s=dict.getStream("stream_out1");
      final Semaphore finished=new Semaphore(0);
     
      s.addSubscriber(new StreamSubscriber() {
        @Override
        public void streamClosed(Stream stream) { }
        int k=0;

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            // System.out.println("tuple: "+tuple);
          int sumy=(Integer)tuple.getColumn(0);
          assertEquals(3*(5*k+10)+5+2,sumy);
          k+=5;
          
          
          if(k>=n-5) finished.release();
        }
      });
   //   long t0=System.currentTimeMillis();
      s.start();
      finished.acquire();
 //     System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
      execute("close stream stream_in");
    }
  
  @Test
  public void testWindow4() throws Exception {
      createFeeder1();
      res=execute("CREATE STREAM stream_out1 AS SELECT aggregatelist(*) from stream_in[SIZE 5 ADVANCE 5 ON x]");
      YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
      Stream s=dict.getStream("stream_out1");
      final Semaphore finished=new Semaphore(0);
     
      s.addSubscriber(new StreamSubscriber() {
        @Override
        public void streamClosed(Stream stream) { }
        int k=0;

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
        //  System.out.println("tuple: "+tuple);
          List<Tuple> ret=(List<Tuple>)tuple.getColumn(0);
          for(Tuple t:ret) {
              assertEquals(k,((Integer)t.getColumn(0)).intValue());
              assertEquals(2*k,((Integer)t.getColumn(1)).intValue());
              k++;
          }
          if(k>=n-5) finished.release();
        }
      });
    //  long t0=System.currentTimeMillis();
      s.start();
      finished.acquire();
  //    System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
      execute("close stream stream_in");
    }
  
  @Test
  public void testWindow5() throws Exception {
      createFeeder1();
      res=execute("CREATE STREAM stream_out1 AS SELECT firstval(x) AS fvx from stream_in[SIZE 5 ADVANCE 5 ON x]");
      YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
      Stream s=dict.getStream("stream_out1");
      final Semaphore finished=new Semaphore(0);
     
      s.addSubscriber(new StreamSubscriber() {
        @Override
        public void streamClosed(Stream stream) { }
        int k=0;

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
          int firstvalx=(Integer)tuple.getColumn(0);
          assertEquals(k,firstvalx);
          k+=5;
          
          
          if(k>=n-5) finished.release();
        }
      });
      //long t0=System.currentTimeMillis();
      s.start();
      finished.acquire();
    //  System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
      execute("close stream stream_in");
    }
  
  @Test
  public void testWindow6() throws Exception {
      createFeeder1();
      res=execute("CREATE STREAM stream_out1 AS SELECT firstval(x+y) from stream_in[SIZE 5 ADVANCE 5 ON x]");
      YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
      Stream s=dict.getStream("stream_out1");
      final Semaphore finished=new Semaphore(0);

      s.addSubscriber(new StreamSubscriber() {
          @Override
          public void streamClosed(Stream stream) { }
          int k=0;

          @Override
          public void onTuple(Stream stream, Tuple tuple) {
              // System.out.println("tuple: "+tuple);
              int fvxpy=(Integer)tuple.getColumn(0);
              assertEquals(3*k,fvxpy);
              k+=5;

              if(k>=n-5) finished.release();
          }
      });
      //long t0=System.currentTimeMillis();
      s.start();
      finished.acquire();
     // System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
      execute("close stream stream_in");
  }
 
  @Test
  public void testWindow7() throws Exception {
      createFeeder1();
      res=execute("CREATE STREAM stream_out1 AS SELECT firstval(x),firstval(y),aggregatelist(*) from stream_in[SIZE 5 ADVANCE 5 ON x]");
      YarchDatabase dict=YarchDatabase.getInstance(context.getDbName());
      Stream s=dict.getStream("stream_out1");
      final Semaphore finished=new Semaphore(0);

      s.addSubscriber(new StreamSubscriber() {
          @Override
          public void streamClosed(Stream stream) { }
          int k=0;

          @Override
          public void onTuple(Stream stream, Tuple tuple) {
              // System.out.println("tuple: "+tuple);
              int fvx=(Integer)tuple.getColumn(0);
              int fvy=(Integer)tuple.getColumn(1);
              
              assertEquals(k,fvx);
              assertEquals(2*k,fvy);
              List<Tuple> ret=(List<Tuple>)tuple.getColumn(2);
              for(Tuple t:ret) {
                  assertEquals(k,((Integer)t.getColumn(0)).intValue());
                  assertEquals(2*k,((Integer)t.getColumn(1)).intValue());
                  k++;
              }

              if(k>=n-5) finished.release();
          }
      });
      //long t0=System.currentTimeMillis();
      s.start();
      finished.acquire();
    //  System.out.println("Pushed "+n+" tuples in "+(System.currentTimeMillis()-t0)+" ms");
      execute("close stream stream_in");
  }


}
