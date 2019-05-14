WebSocket
=========

Yamcs provides a WebSocket API for data subscriptions. A typical use case would be a display tool subscribing to parameter updates.

The WebSocket supports two subprotocols:

1. Textual WebSocket frames encoded in JSON
2. Binary WebSocket frames encoded in Google Protocol Buffers

To select one or the other specify this header on your WebSocket upgrade request::

    Sec-WebSocket-Protocol: protobuf

or::

    Sec-WebSocket-Protocol: json

When unspecified, the server defaults to JSON.


Wrapper
-------

WebSocket calls should be directed to a URL of the form:

    http://localhost:8090/_websocket/:instance

Replace ``:instance`` with your Yamcs instance name. The frame must contain a text array like so:: json

    [x,y,z,{"<request-type>":"<request>"}]

Where:

x
    the version of the protocol (currently fixed at 1)

y
    the message type. One of:

    * ``1`` Request
    * ``2`` Reply
    * ``3`` Exception
    * ``4`` Data

z
    a sequence counter. Enables clients to couple a response with the original request

The ``request-type`` and ``request`` criteria vary for every type of resource, and are each time indicated in further pages.
