Replication
===========

This page shows information on active replication streams between Yamcs instances.


Inbound
-------

Inbound replication means that the data is incoming to the local Yamcs server.

Instance
    Name of the local Yamcs instance where replicated stream tuples are injected.

Streams
    The replicated streams. These must match between master and slave.

Mode
    One of PUSH or PULL. In PUSH mode, the TCP connection is initiated by the remote master. In PULL mode, the TCP connection is initiated locally by the slave.

Local address
    Local host and port information for this replication connection.

Remote address
    Remote host and port information for this replication connection.

Pull from
    Name of the remote Yamcs instance. This is empty when the data is being pushed into the local instance by the remote master (the remote decides where to push).


Outbound
--------

Outbound replication means that the data is outgoing to a remote Yamcs server.

Instance
    Name of the local Yamcs instance whose streams are replicated.

Streams
    The replicated streams. These must match between master and slave.

Mode
    One of PUSH or PULL. In PUSH mode, the TCP connection is initiated by the local master. In PULL mode, the TCP connection is initiated by the remote slave.

Local address
    Local host and port information for this replication connection.

Remote address
    Remote host and port information for this replication connection.

Push to
    Name of the remote Yamcs instance. This is empty when the data is being pulled by the remote slave (the remote will decide for itself).
