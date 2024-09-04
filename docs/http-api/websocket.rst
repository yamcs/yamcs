WebSocket
=========

Yamcs provides a WebSocket API for data subscriptions. A typical use case would be a display tool subscribing to parameter updates. But you could also subscribe to realtime events, alarms or even raw packets.

WebSocket allows to upgrade a regular HTTP connection to a bi-directional communication channel. Yamcs supports an RPC-style API over this channel where clients choose what topics they want to subscribe (or unsubscribe) by sending a request in a specific format.


Connection
----------

WebSocket calls should use a URL of the form ``http://localhost:8090/api/websocket``

We suggest using a generic library for establishing a WebSocket connection because the protocol is quite involving.

On the server-side, Yamcs supports two WebSocket subprotocols:

1. Textual WebSocket frames encoded in JSON
2. Binary WebSocket frames encoded in Google Protocol Buffers

To select one or the other specify this header on your WebSocket upgrade request::

    Sec-WebSocket-Protocol: protobuf

or::

    Sec-WebSocket-Protocol: json

When unspecified, the server defaults to JSON. These two formats are functionally identical.

.. note::
    For readability purposes, the next sections focus on JSON.


Client Message
--------------

A message sent by the client to Yamcs must always have this general form:

.. code-block:: typescript

    {
      "type": string,
      "options": any | undefined,
      "id": number | undefined,
      "call": number | undefined
    }

Where:

type
    The message type. Typically this is the topic to subscribe to, but it could also be a built-in like ``cancel``.

options
    Options specific to the type.

id
    An optional client-side message identifier. If you specify this, then Yamcs will return it in reply messages. This purpose of this property is to allow clients to correlate replies with the original request. This is necessary because Yamcs does not guarantee in-order delivery of replies with respect to client requests.

    We recommend to use an incrementing number. Yamcs does not currently check on continuity, but it is something we may consider later on.

call
    Where applicable, this must contain the call associated with this message. This should only be used when the client is streaming multiple messages handled by the same call. Client-streaming is rarely used, so chances are that you will never need to use this option.


Built-in Client messages
^^^^^^^^^^^^^^^^^^^^^^^^

.. rubric:: Cancel

.. code-block:: typescript

    {
      "type": "cancel",
      "options": {
        "call": number
      }
    }

This message allows to cancel an ongoing call. The call to cancel must be specified as part of the message options.


.. rubric:: State

.. code-block:: typescript

    {
      "type": "state"
    }

In response to this message, Yamcs will dump a snapshot of the active calls on the current connection. This is intended for debugging reasons.


Server Messages
---------------

A message sent by the Yamcs to the client will always have this general form:

.. code-block:: typescript

    {
      "type": string,
      "call": number | undefined,
      "seq": number | undefined,
      "data": any
    }

Where:

type
    The message type. Typically this is the topic that was subscribed to, but it could also be a built-in like ``reply``.

call
    Where applicable, this contains the call identifier for this message. For the typical case of server-streams, all server messages for a single client request, have the same call identifier.

seq
   This is a sequence counter scoped to the call. The purpose of this is so that client could detect when some messages have been skipped. Yamcs applies a WebSocket-wide mechanism whereby frames are dropped if the client is not reading fast enough. If enough frames are dropped, the client connection may even be closed.

data
    Data associated with this type of server message.


Built-in Server messages
^^^^^^^^^^^^^^^^^^^^^^^^

.. rubric:: Reply

.. code-block:: typescript

    {
      "type": "reply",
      "call": number,
      "seq": number,
      "data": {
        "reply_to": number,
        "exception": any | undefined
      }
    }

This message is sent by the server in response to a topic request. Yamcs guarantees that this reply message is sent before any other topic messages. The field ``reply_to`` contains a reference to the ``id`` from the original client message. If there was an error in handling the request, the reply will provide exception details. This is an object that follows the same structure as exceptions on the regular HTTP API.

.. rubric:: State

.. code-block:: typescript

    {
      "type": "state",
      "data": {
        "calls": [
          {
            "call": number,
            "type": string,
            "options": any | undefined
          },
          ...
        ]
      }
    }

This message is sent in response to a request of type ``state``. It dumps a list of all active calls. The intended use is for debugging issues. Client that support reconnection cannot rely on this information because it will no longer be present when a new connection is established.


Example
-------

A simple Hello World example would be to subscribe to time updates coming from the server. Assuming that your Yamcs server has an instance called ``myproject``, you would send a message like this indicating your interest:

.. code-block:: json

    {
      "type": "time",
      "id": 1,
      "options": {
        "instance": "myproject"
      }
    }

To confirm your request, Yamcs will first send you a reply that looks somewhat like this:

.. code-block:: json

    {
      "type": "reply",
      "call": 3,
      "seq": 72,
      "data": {
        "@type": "/yamcs.api.Reply",
        "reply_to": 1
      }
    }

As the client, we note that the server has assigned the call identifier ``3`` to this subscription.

.. note::
    The property ``@type`` is an artifact generated by Yamcs JSON backend. It specifies the equivalent Protobuf message type of the ``data`` object (Yamcs generates JSON based on Protobuf definitions). You may ignore this property because each message ``type`` uses only a single ``data`` message.


Next we receive continued time updates, each in a WebSocket frame:

.. code-block:: json

    {
      "type": "time",
      "call": 3,
      "seq": 73,
      "data": {
        "@type": "/google.protobuf.Timestamp",
        "value": "2020-05-14T06:44:32.654Z"
      }
    }

.. code-block:: json

    {
      "type": "time",
      "call": 3,
      "seq": 74,
      "data": {
        "@type": "/google.protobuf.Timestamp",
        "value": "2020-05-14T06:44:33.656Z"
      }
    }

Note that each of these updates can be linked to the call identifier ``3``. If you had multiple subscriptions going on, this would allow you to couple messages to the correct local handler.

Once you're no longer interested to receive updates for this particular call, you can cancel it like this:

.. code-block:: json

    {
      "type": "cancel",
      "options": {
        "call": 3
      }
    }

Of course, if you have no plans to use this connection for other calls, you could as well have closed it altogether.
