Replication Master
==================


Replicates data streams to remote servers. Works both in TCP server and TCP client mode. In TCP server mode, it relies on the :doc:`../global/replication-server` to provide the TCP connectivity.

In TCP client mode, it connects to a list of slaves specified in the configuration.

The master works by storing stream of tuples serialized in memory mapped files :javadoc:`org.yamcs.replication.ReplicationFile`. Each tuple receives a 64 bit incremental transaction id. In addition to the tuple data, there are some metadata transactions storing information about the streams and allowing the data to be compressed. For example a parameter tuple has the potentially very long qualified parameter names as column names, these are only stored in the metadata and replaced in the data by 32 bit integers. The serialization mechanism is the same used for serializing tuples in the stream archive but there is no distinction between the key and the value.

The replication files are append only (except for a header which contains the number of tuples stored) and contain a configurable number of tuples. The maximum size of the file is also configurable so a new file is created either when the maximum number of transactions has been reached or when the maximum size of the file has been reached.

The replication slaves are responsible for keeping track of their last received transaction id. In both TCP client and server mode, the slaves are sending to the master the first transaction id and the master starts replaying from there. In case the slave has not connected for a long time, the first transaction may be in one of the deleted files. The master will start sending from the first transaction available.

New in version 5.6.1: the master will regularly send time messages in order to keep the connection alive if there is no data. The slave can optionally use the time message to update the local mission time, synchronizing it to the master.


Class Name
----------

:javadoc:`org.yamcs.replication.ReplicationMaster`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

  services:
      - class: org.yamcs.replication.ReplicationMaster
        args:
            tcpRole: client
            pageSize: 500
            maxPages: 500
            streams: ["tm_realtime", "tm2_realtime"]
            maxFileSizeKB: 102400
            expirationDays: 7
            fileCloseTimeSec: 300
            slaves:
                - host: "localhost"
                  port: 8099
                  instance: "node2"
                  enableTls: false
            reconnectionInterval: 5000

Configuration Options
---------------------

tcpRole  (string)
    **Required** One of client or server.

maxPages (integer)
    The number of pages of the replication file. The replication file header contains an index allowing to access the start of each page. Thus more pages, the faster is to jump to a given transaction but the larger the header. Since seeking a transaction is only performed when a slave connects, it is not critical that the search is very fast. The total number of transactions in one file is ``maxPages`` times ``pageSize``. Default: 500

pageSize (integer)
    The number of transactions on one page. Default: 500
 
streams (list of strings)
    The list of streams that will be replicated. The replication file will contain multiplexed data from these streams in order in which the data is generated. The connected slaves will receive data from all streams but they may filter it out locally.
    
maxFileSizeKB (integer)
    Maximum size in KB of the replication file. Default 102400 (e.g. the maximum file size will be 100 MB).
 
fileCloseTimeSec (integer)
    How many seconds to keep a file open after being accessed by a slave. Default: 300.

expirationDays (double)
    How many days to keep the replication files before removing them. Default: 7

slaves (list of maps)
    **Required** if the ``tcpRole`` is ``client``. The list of slaves to connect to. Each slave is specified as a host/port and the slave instance name. In addition, TLS (encrypted connections) can be specified for each slave individually using the ``enableTls`` option.

    The replication master will connect to the replication server on the remote host/port and will send a Wakeup message containing the salve instance name; the replication server will then redirect the connection to the corresponding replication slave if one has registered for the given instance.

reconnectionIntervalSec (integer)
    If the ``tcpRole`` is ``client`` this configures how often in seconds the replication master will try to connect to the salve if the connection is broken. A negative value means that no reconnection will take place.

timeMsgFreqSec (integer)
    Added in version 5.6.1. How often (in seconds) should send the time messages. Default: 10
