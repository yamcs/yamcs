package org.slf4j.impl;

import org.slf4j.Logger;
import org.slf4j.impl.JDK14LoggerAdapter;

/**
 * 
 * @author nm
 *
 * This is a temporary workaround for the bug http://jira.qos.ch/browse/SLF4J-382
 * that causes loggers with dynamic names to not get garbage collected (because they are collected in a map part of slf4j).
 * 
 * 
 * It always uses JUL (it could be made to use the other bindings of slf4j but hopefully that bug will be fixed soon enough).
 * 
 */
public class YamcsLoggerFactory {
    public static Logger getLogger(String name) {
        // the root logger is called "" in JUL
        if (name.equalsIgnoreCase(Logger.ROOT_LOGGER_NAME)) {
            name = "";
        }

        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(name);
        return new JDK14LoggerAdapter(julLogger);
    }
}
