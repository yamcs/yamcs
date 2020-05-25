Replication Slave
==================


The slave counterpart to the :doc:`replication-master`.  It receives serialized tuple data from the master and injects it in the local stream. Works both in TCP server and TCP client mode. In TCP server mode, it relies on the :doc:`../global/replication-server` to provide the TCP connectivity. 
In TCP client mode, it connects to the master defined in the configuration.

The slave keeps track of the id of the last transaction received from the master in a local text file ``<yamcs-data/instance/replication/slave-lastid.txt``. Each time the connection to the master is estabilished, it sends a request containing the last transaction id +1. The master will start replaying data from that transaction.



Class Name
----------

:javadoc:`org.yamcs.replication.ReplicationSlave`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

.. code-block:: yaml

  services:
      - class: org.yamcs.replication.ReplicationSlave
        args:
            tcpRole: client
            masterHost: localhost
            masterPort: 8099
            masterInstance: node1
            streams: ["tm_realtime", "sys_param"]

              
Configuration Options
---------------------

tcpRole  (string)
    **Required** One of client or server.

masterHost (string)
    **Required** if the tcpRole is `client`. The hostname of the master. Not relevant if the tcpRole is `server`.
    
masterPort (integer)
    **Required** if the tcpRole is `client`. The port of the master.  Not relevant if the tcpRole is `server`.
    
masterInstance (string)
    **Required** if the tcpRole is `client`. The instance of the master. When working in `server` tcp mode, the instance on which the master is configured determines the data which will be passed to the slave. If two masters try to connect to the same slave, only the first connection will be accepted. 
 
streams (list of strings)
    The list of streams that will be processed. The master may send data from other streams but they will be filtered out.

    
reconnectionInterval (integer)
    If the tcpRole is `client` this configures how often in milliseconds the slave will try to connect to the master if the connection is broken. A negative value means that no reconnection will take place.
               
