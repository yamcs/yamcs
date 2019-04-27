Edit Processor
==============

Edit a processor::

    PATCH /api/processors/:instance/:name

.. note::

    Only replay processors can be edited.


.. rubric:: Parameters

state (string)
    The state this replay processor should be updated to. Either ``paused`` or ``running``.

seek (string)
    The time where the processing needs to jump towards. Must be a date string in ISO 8601 format.

speed (string)
    The speed of the processor. One of:

    * ``afap``
    * a speed factor relative to the original speed. Example: ``2x``
    * a fixed delay value in milliseconds. Example: ``2000``


.. rubric:: Example

Pause the processor:

.. code-block:: json

    {
      "state" : "paused"
    }

Resume the processor, and set speed to 2.5x:

.. code-block:: json

    {
      "state" : "running",
      "speed" : "2.5x"
    }

Make processor move according to original speed:

.. code-block:: json

    {
      "speed" : "1x"
    }


.. rubric:: Request Schema (Protobuf)
.. code-block:: proto

    message EditProcessorRequest {
      optional string state = 1;
      optional string seek = 2;
      optional string speed = 3;
    }
