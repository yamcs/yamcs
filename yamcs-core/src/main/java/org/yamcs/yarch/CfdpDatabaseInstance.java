package org.yamcs.yarch;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles tables and streams for one Yamcs Instance
 * 
 * 
 * Synchronisation policy: to avoid problems with stream disappearing when clients connect to them, all the
 * creation/closing/subscription to streams/tables shall be done while acquiring a lock on the YarchDatabase object.
 * This is done in the StreamSqlStatement.java
 * 
 * Delivery of tuples does not require locking, this means subscription can change while delivering (for that a
 * concurrent list is used in Stream.java)
 * 
 * @author nm
 *
 */
public class CfdpDatabaseInstance {
    static Logger log = LoggerFactory.getLogger(CfdpDatabaseInstance.class.getName());

    CfdpCollection cfdpDatabase;

    // yamcs instance name (used to be called dbname)
    private String instanceName;

    CfdpDatabaseInstance(String instanceName) throws YarchException {
        this.instanceName = instanceName;
    }

    /**
     * 
     * @return the instance name
     */
    public String getName() {
        return instanceName;
    }

    public String getYamcsInstance() {
        return instanceName;
    }

    public List<CfdpTransfer> getCfdpTransfers() {
        return this.cfdpDatabase.getTransfers();
    }
}
