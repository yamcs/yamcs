Edit Alarm
==========

Edit an alarm::

    PATCH /api/processors/:instance/:processor/parameters/:namespace/:name/alarms/:seqnum


.. rubric:: Parameters

state (string)
    **Required.** The state of the alarm. Either ``acknowledged`` or ``unacknowledged``.

comment (string)
    Message documenting the alarm change.


.. rubric:: Example
.. code-block:: json

    {
      "state": "acknowledged",
      "comment": "bla bla"
    }


.. rubric:: Request Schema (Protobuf)
.. code-block:: proto

    message EditAlarmRequest {
      optional string state = 1;
      optional string comment = 2;
    }
