package org.yamcs.parameterarchive;

import me.lemire.integercompression.FastPFOR128;

/**
 * Because the FastPFOR codec uses quite some memory, 
 * we use this factory to limit the number of created objects to one per thread
 * 
 * 
 * @author Nicolae Mihalache
 *
 */
public class FastPFORFactory {
    static ThreadLocal<FastPFOR128> tl = new ThreadLocal<FastPFOR128>(){
        @Override
        protected FastPFOR128 initialValue() {
            return new FastPFOR128();
        };
    };
    
    public static FastPFOR128 get() {
        return tl.get();
    }
}
