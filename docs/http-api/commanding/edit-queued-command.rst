Edit Queued Command
===================

Edit a command queue entry::

    PATCH /api/processors/:instance/:processor/cqueues/:cqueue/entries/:uuid


.. rubric:: Parameters

state (string)
    The state of the entry. Either ``released`` or ``rejected``.

The same parameters can also be specified in the request body. In case both query string parameters and body parameters are specified, they are merged with priority being given to query string parameters.


.. rubric:: Example

Release an entry:

.. code-block:: json

    {
      "state" : "released"
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message EditCommandQueueEntryRequest {
      optional string state = 1;
    }
