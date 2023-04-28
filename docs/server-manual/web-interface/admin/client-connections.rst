Client Connections
==================

This page shows current HTTP connections. Yamcs supports HTTP persistent connections (HTTP Keep-Alive).

Id
    Short channel identifier.

Protocol
    HTTP protocol version. Note that Yamcs does not currently support HTTP/2 or HTTP/3.

Remote address
    Client IP and port.

Read
    Cumulative read bytes.

Written
    Cumulative written bytes.

Rx
    Read throughput in the last check interval.

Tx
    Write throughput in the last check interval.


A check interval of 5 seconds is used to determine HTTP traffic metrics.
