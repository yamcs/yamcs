Edit Service
============

Edit a global service::

    PATCH /api/services/_global/:name

Edit a service for a specific Yamcs instance::

    PATCH /api/services/:instance/:name


.. rubric:: Parameters

state (string)
    | The state of the service. Either ``running`` or ``stopped``.
    | Note that once stopped, a service cannot be resumed. Instead a new service instance will be created and started.


.. rubric:: Example

Start a service:

.. code-block:: json

    {
      "state" : "running"
    }


Stop a service:

.. code-block:: json

    {
      "state" : "stopped"
    }
