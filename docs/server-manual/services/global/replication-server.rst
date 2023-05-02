Replication Server
==================


The replication server facilitates the communication between :doc:`../instance/replication-master` and :doc:`../instance/replication-slave`. The master and slaves defined with the tcpRole ``server`` will register to this component to be called when an external TCP client connects. Multiple master and slaves from different Yamcs instances in the same Yamcs server will register to the same replication server.

A remote slave when connecting will send a request message indicating the instance and the transaction it wants to start the replay with. The replication server will forward the request to the registered local master which will immediately start the replay.

A remote master when connecting to the replication server will send a wakeup message indicating the instance of the slave. The replication server will redirect the message to the registered local slave which in turn will send a request to the master indicating the transaction start.



Class Name
----------

:javadoc:`org.yamcs.replication.ReplicationServer`


Configuration
-------------

This service is defined in :file:`etc/yamcs.yaml`. Example:

.. code-block:: yaml

  services:
      - class: org.yamcs.replication.ReplicationServer
        args:
           port: 8099
           tlsCert: /path/to/server.crt
           tlsKey: /path/to/server.key
           maxTupleSize: 131072

              
Configuration Options
---------------------

port  (integer)
    **Required** The port to listen for TCP connections.               

tlsCert (string or list of strings)
    If specified, the server will be listening for TLS connections. TLS is used for encrypting the data, client certificates are not supported. If TLS is enabled, all connections have to be encrypted, the server does not support TLS and non-TLS connections simultaneously.

    In case the file is a bundle containing multiple certificates, the certificates must be ordered from leaf to root.

    Multiple certificate files may also be provided as an array. Again, certificates must then be ordered from leaf to root, between the files and also between certificates within the files.

tlsKey (string)
    **Required** if ``tlsCert`` is specified. The key to the certificate.

maxTupleSize (integer)
    Used for the slaves with tcpRole = server - configures the maximum size of the serialized tuples received from the master. If the serialized tuples are larger than this size, this limit has to be increased otherwise the tuples cannot be transferred. Default: 131072 (128 KB).
