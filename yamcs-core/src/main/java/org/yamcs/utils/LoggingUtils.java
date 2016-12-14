package org.yamcs.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.YamcsLoggerFactory;
import org.yamcs.YProcessor;
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
    public static Logger getLogger(Class<?> clazz, YProcessor processor) {
        return LoggerFactory.getLogger(clazz.getName() + "["+processor.getInstance()+"/" +processor.getName()+ "]");
    }

    public static Logger getLogger(Class<?> clazz, String instance, TableDefinition tblDef) {
        return YamcsLoggerFactory.getLogger(clazz.getName() + "["+instance+"/"+tblDef.getName()+"]");
    }
    public static Logger getLogger(Class<?> clazz, String instance, Stream stream) {
        return YamcsLoggerFactory.getLogger(clazz.getName() + "["+instance+"/"+stream.getName()+"]");
    }
}
