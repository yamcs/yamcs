Download Commands
=================

Download command history::

    GET /api/archive/:instance/downloads/commands

.. note::

    This operation will possibly download a very large file.


.. rubric:: Parameters

start (string)
    Filter the lower bound of the command's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the command's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``


.. rubric:: Response

The response is a stream of self-standing command history records.


.. rubric:: Response Schema (protobuf)

The response is a stream of individual Protobuf messages delimited by a ``VarInt``. Messages are of type:

.. code-block:: proto

    message CommandHistoryAttribute {
      optional string name = 1;
      optional yamcs.Value value = 2;
      optional int64 time = 3;
    }

    message CommandHistoryEntry {
      required CommandId commandId = 1;
      repeated CommandHistoryAttribute attr = 3;
    }
