Edit Instance
=============

Edit an instance::

    PATCH /api/instances/:instance

.. rubric:: Parameters

state (string)
    The state this instance should be updated to. One of:

    * ``stopped`` - stop all services of the instance. The instance state will be OFFLINE. If the instance state is already OFFLINE, this call will do nothing.
    * ``restarted`` - if the instance state is RUNNING, the instance will be stopped and then restarted. Otherwise the instance will be started. Note that the Mission Database will be also reloaded before restart.
    * ``running`` - start the instance. If the instance is in the RUNNING state, this call will do nothing. Otherwise the instance will be started.


.. rubric:: Example

.. code-block:: text

    PATCH /api/instances/simulator?state=stop

.. code-block:: json

    {
      "name": "simulator",
      "missionTime": "2019-03-12T11:06:08.514Z",
      "state": "OFFLINE"
    }

.. code-block:: text

     PATCH /api/instances/simulator?state=running

.. code-block:: json

    {
      "name": "simulator",
      "missionTime": "2019-03-12T11:06:08.514Z",
      "state": "RUNNING"
    }
