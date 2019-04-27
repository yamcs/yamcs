Download Packets
================

Download archived packets::

    GET /api/archive/:instance/downloads/packets


.. rubric:: Parameters

name (array of strings)
    The archived name of the packets. Names must match exactly. Both these notations are accepted:

    * ``?name=/YSS/SIMULATOR/DHS,/YSS/SIMULATOR/Power``
    * ``?name[]=/YSS/SIMULATOR/DHS&name[]=/YSS/SIMULATOR/Power``

start (string)
    Filter the lower bound of the packet's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the packet's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``


.. rubric:: Response

The response is a stream of self-standing JSON messages.


.. rubric:: Response Schema (protobuf)

The response is a stream of self-standing ``VarInt`` delimited messages of type:

.. code-block:: proto

    message TmPacketData {
      required int64 receptionTime = 1;
      required bytes packet = 2;
      optional int64 generationTime = 3;
      optional int32 sequenceNumber = 4;
      optional NamedObjectId id = 5;
    }


.. rubric:: Raw Binary

Download raw packet binary by using this HTTP header:

    Accept: application/octet-stream

Or add the query parameter `format=raw`.
