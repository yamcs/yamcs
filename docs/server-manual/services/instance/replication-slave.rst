Replication Slave
==================


The slave counterpart to the :doc:`replication-master`.  It receives serialized tuple data from the master and injects it in the local stream. Works both in TCP server and TCP client mode. In TCP server mode, it relies on the :doc:`../global/replication-server` to provide the TCP connectivity. 
In TCP client mode, it connects to the master defined in the configuration.

The slave keeps track of the id of the last transaction received from the master in a local text file :file:`{yamcs-data}/{instance}/replication/slave-lastid.txt`. Each time the connection to the master is established, it sends a request containing the last transaction id, plus one. The master will start replaying data from that transaction. If the replication slave does not find the file at startup, it will receive all the data that the master has.

There can be two or replication slaves running for the same instance, connected to two different masters.

To avoid an infinite message flood caused by a miss-configuration whereby a slave receives and inserts into a stream the data which was extracted from the same stream, each incoming messages contains a 32 bit ``instance id``. This is the id of the instance where the message has originated from. If a slave receives a message with its own instance id it will discard it and not insert it into the stream.

The instance id is calculated as a hash code from the ``<serverId>.<instanceName>``. The serverId is by default the hostname but can be changed in :file:`etc/yamcs.yaml`.

New in version 5.6.1: the master will regularly send time messages in order to keep the connection alive if there is no data. The slave can optionally use the time message to update the local mission time, synchronizing it to the master.


Class Name
----------

:javadoc:`org.yamcs.replication.ReplicationSlave`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

  services:
      - class: org.yamcs.replication.ReplicationSlave
        args:
            tcpRole: client
            masterHost: localhost
            masterPort: 8099
            masterInstance: node1
            enableTls: false
            reconnectionIntervalSec: 30
            streams: ["tm_realtime", "sys_param"]
            lastTxFile: "slave-lastid.txt"


Configuration Options
---------------------

tcpRole  (string)
    **Required** One of client or server.

masterHost (string)
    **Required** if the ``tcpRole`` is ``client``. The hostname of the master. Not relevant if the ``tcpRole`` is ``server``.
    
masterPort (integer)
    **Required** if the ``tcpRole`` is ``client``. The port of the master.  Not relevant if the ``tcpRole`` is ``server``.
    
masterInstance (string)
    **Required** if the ``tcpRole`` is ``client``. The instance of the master. When working in ``server`` tcp mode, the instance on which the master is configured determines the data which will be passed to the slave. If two masters try to connect to the same slave, only the first connection will be accepted. 

enableTls (boolean)
     **Required**  Used when ``tcpRole`` is ``client``. If true, a TLS connection will be attempted. The server provided certificate will be checked against the trustStore in Yamcs :file:`etc/` directory. If the ``tcpRole`` is ``server`` the usage or not of TLS is determined by the configuration of the :doc:`../global/replication-server`.
     
reconnectionIntervalSec (integer)
    If the ``tcpRole`` is ``client`` this configures how often in seconds the slave will try to connect to the master if the connection is broken. A negative value means that no reconnection will take place. Default: 30
               
streams (list of strings)
    The list of streams that will be processed. The master may send data from other streams but they will be filtered out.

lastTxFile (String)
    The name of file where the slave will keep track of the last transaction id received from the server. It defaults to the ``<service-name>-lastid.txt``

maxTupleSize (integer)
    if the ``tcpRole`` is ``client`` this configures the maximum size of one message received from the master.  If the serialized tuples are larger than this size, this limit has to be increased otherwise the tuples cannot be transferred. Default 131072 (128KB).

timeoutSec (float)
    Added in version 5.6.1. Timeout (in seconds) for detecting broken connections. If no message is received in this time from the master, the connection will be closed. Even if there is no data, the master sends a time message at configurable intervals.

    Default: 30.

updateSimTime (boolean)
    Added in version 5.6.1. If true, update the simulation time with the time received from the master in the time messages, allowing to synchronize the mission time between the master an the slave. This only works if the ``SimulationTimeService`` is configured on the same instance with this service. The time0 will be set to 0 at the service startup. The messages received regularly from the master contain the triplet (localTime, missionTime, speed) and will be used to call the methods ``setSimElapsedTime(long javaTime, long simElapsedTime)`` and ``setSpeed(double speed)`` in the ``SimulationTimeService``.
    
    The synchronization relies on the fact that the local (UNIX) times are synchronized between master and slave. This has to be ensured at the system level (e.g. using NTP).

    Default: false
