Edit Client
===========

Edit a client::

    PATCH /api/clients/:id


.. rubric:: Parameters

instance (string)
    The instance. If unspecified, this defaults to the instance that the client is currently connected to.

processor (string)
    The processor. If ``instance`` is provided and ``processor`` is not, then this will default to the default processor for that instance.


.. rubric:: Example

Update the client's processor to ``replay123``:

.. code-block:: json

    {
      "processor" : "replay123"
    }

Later on, leave the replay and switch to the default processor (e.g. realtime) on the simulator instance:

.. code-block:: json

    {
      "instance" : "simulator"
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message EditClientRequest {
      optional string instance = 1;
      optional string processor = 2;
    }
