package org.yamcs.utils;

import org.slf4j.Logger;
import org.slf4j.impl.YamcsLoggerFactory;
import org.yamcs.Processor;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;

public class LoggingUtils {

    /**
     * Return a logger decorated with the applicable yamcs instance
     * @param clazz 
     * @param instance 
     * @return a newly created logger
     */
    public static Logger getLogger(Class<?> clazz, String instance) {
        return YamcsLoggerFactory.getLogger(clazz.getName() + "["+instance+"]");
    }

    /**
     * Return a logger decorated with the applicable yamcs instance and processor
     * @param clazz 
     * @param processor 
     * @return a newly created logger
     */
    public static Logger getLogger(Class<?> clazz, Processor processor) {        
        //TODO - we really want to have the name of the processor in the logs but unfortunately JUL will make a node for it in the logging hierarchy 
        // and and it will never be garbage collected causing the memory to ever increase when processors with random names are created 
        //    (such as those from REST replays or from ParameterArchive fillup)
        // same is below for streams
        
        //return YamcsLoggerFactory.getLogger(clazz.getName() + "["+processor.getInstance()+"/" +processor.getName()+ "]");                
        return YamcsLoggerFactory.getLogger(clazz.getName() + "["+processor.getInstance()+"]");
    }

    public static Logger getLogger(Class<?> clazz, String instance, TableDefinition tblDef) {
        return YamcsLoggerFactory.getLogger(clazz.getName() + "["+instance+"/"+tblDef.getName()+"]");
    }
    public static Logger getLogger(Class<?> clazz, String instance, Stream stream) {
        //TODO see above
        //return YamcsLoggerFactory.getLogger(clazz.getName() + "["+instance+"/"+stream.getName()+"]");
        return YamcsLoggerFactory.getLogger(clazz.getName() + "["+instance+"]");
    }
}
