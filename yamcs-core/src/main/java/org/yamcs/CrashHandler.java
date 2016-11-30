package org.yamcs;

/**
 * 
 * CrashHandler is used to handle extreme problems that need to reach operator (or sysadmin) attention.
 * 
 * 
 * @author nm
 *
 */
public interface CrashHandler {
    public void handleCrash(String type, String msg);
}
