Create Event
============

Create an event for the given Yamcs instance:

    POST /api/archive/:instance/events/


.. rubric:: Parameters

message (string)
    **Required.** Event message.

type (string)
    Description of the type of the event. Useful for quick classification or filtering.

severity (string)
    The severity level of the event. One of ``info``, ``watch``, ``warning``, ``distress``, ``critical`` or ``severe``. Default is ``info``

time (string)
    Time associated with the event. Must be a date string in ISO 8601 format. If unspecified, this will default to the current mission time.

source (string)
    Source of the event. Useful for grouping events in the archive. Default is ``User``.

sequence_number (integer)
    Sequence number of this event. This is primarily used to determine unicity of events coming from the same source. If not set Yamcs will automatically assign a sequential number as if every submitted event is unique.


.. rubric:: Example

Create an informatory event at the current mission time:

.. code-block:: json

    {
      "message": "Some info message"
    }

Add a critical event in the past:

.. code-block:: json

    {
      "message":"Some critical message",
      "severity": "critical",
      "time": "2015-01-01T00:00:00.000Z",
    }


.. rubric:: Response

The full event is returned in the response body, including fields that are added by Yamcs Server.

.. code-block:: json

    {
      "source": "User",
      "generationTime": "1524258406719",
      "receptionTime": "1524258406719",
      "seqNumber": 0,
      "message": "Some info message",
      "severity": "INFO",
      "generationTimeUTC": "2018-04-20T21:06:09.719Z",
      "receptionTimeUTC": "2018-04-20T21:06:09.719Z",
      "createdBy": "admin"
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message CreateEventRequest {
      optional string type = 1;
      optional string message = 2;
      optional string severity = 3;
      optional string time = 4;
      optional string source = 5;
      optional int32 sequenceNumber = 6;
    }
