package org.yamcs.parameterarchive;

import java.util.function.Consumer;

import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.parameter.Value;

public class SegmentIterator {
    final SortedTimeSegment timeSegment;
    final ValueSegment valueSegment;
    final ValueSegment rawValueSegment;
    final ParameterStatusSegment parameterStatusSegment;
    
    final long start;
    final long stop;
    final boolean ascending;
    int pos;
    
    public SegmentIterator(SortedTimeSegment timeSegment, ValueSegment valueSegment, ValueSegment rawValueSegment, ParameterStatusSegment parameterStatusSegment,
            long start, long stop, boolean ascending) {
        this.start = start;
        this.stop = stop;
        this.ascending = ascending;
        this.timeSegment = timeSegment;
        this.valueSegment = valueSegment;
        this.rawValueSegment = rawValueSegment;
        this.parameterStatusSegment = parameterStatusSegment;
        
        init();
    }

   
    
    private void init() {
        if(ascending) {
            if(start < timeSegment.getSegmentStart()) {
                pos = 0;
            } else {                          
                pos = timeSegment.search(start);
                if(pos<0) pos = -pos-1;
            }
        } else {
            if(stop > timeSegment.getSegmentEnd()) {
                pos = timeSegment.size()-1;
            } else {                          
                pos = timeSegment.search(stop);
                if(pos<0)  pos = -pos-2;
            }
        }
    }
    
    boolean hasNext() {
        if(ascending) {
            return pos < timeSegment.size() && timeSegment.getTime(pos)<stop;
        } else {
            return pos>=0 && timeSegment.getTime(pos)>start;
        }
    }
    
    public TimedValue next() {
        long t = timeSegment.getTime(pos);
        Value ev = (valueSegment==null)?null:valueSegment.getValue(pos);
        Value rv = (rawValueSegment==null)?null:rawValueSegment.getValue(pos);
        ParameterStatus ps = (parameterStatusSegment==null)?null:parameterStatusSegment.get(pos);
        
        if(ascending) {
            pos++;
        } else {
            pos--;
        }
        return new TimedValue(t,ev, rv, ps);
    }

    public void forEachRemaining(Consumer<TimedValue> consumer) {
        while(hasNext()) {
            long t = timeSegment.getTime(pos);
            Value ev = (valueSegment==null)?null:valueSegment.getValue(pos);
            Value rv = (rawValueSegment==null)?null:rawValueSegment.getValue(pos);
            ParameterStatus ps = (parameterStatusSegment==null)?null:parameterStatusSegment.get(pos);

            if(ascending) {
                pos++;
            } else {
                pos--;
            }
            consumer.accept(new TimedValue(t, ev, rv, ps));
        }
    }
        
    public interface SimpleValueConsumer {
        public void accept(long t, Value v);
    }
}
