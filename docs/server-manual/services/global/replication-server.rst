Replication Server
==================


The replication server facilitates the communication between :doc:`../instance/replication-master` and :doc:`../instance/replication-slave`. The master and slaves defined with the tcpRole `server` will register to this component to be called when an external tcp client connects. Multiple master and slaves from different Yamcs instances in the same Yamcs server will register to the same replication server.

A remote slave when connecting will send a request message indicating the instance and the transaction it wants to start the replay with. The replication server will forward the request to the registered local master which will immediately start the replay.

A remote master when connecting to the replication server will send a wakeup message indicating the instance of the slave. The replication server will redirect the message to the registered local slave which in turn will send a request to the master indicating the transaction start.



Class Name
----------

:javadoc:`org.yamcs.replication.ReplicationServer`


Configuration
-------------

This service is defined in ``etc/yamcs.yaml``. Example:

.. code-block:: yaml

  services:
      - class: org.yamcs.replication.ReplicationServer
        args:
           port: 8099

              
Configuration Options
---------------------

port  (integer)
    **Required** The port to listen for TCP connections.               
