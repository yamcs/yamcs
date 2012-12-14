package org.yamcs.cmdhistory;

import java.util.Map;

import org.yamcs.commanding.PreparedCommand;


/**
 * An entry in the command history.
 * @author mache
 *
 */
public class CommandHistoryEntry {
    public int command_id; //this is the key in the database, should get rid of it in favor of pc.id
    public PreparedCommand pc;
    public Map<String, String> extensionValues=null;
    long lastUpdated;//time in milisec of the last update
}