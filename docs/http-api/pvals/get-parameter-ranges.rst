Get Parameter Ranges
====================

Retrieve a history of ranges for the specified parameter::

    GET /api/archive/:instance/parameters/:namespace/:name/ranges

A range is a tuple ``(start, stop, value, count)`` that represents the time interval for which the parameter has been steadily coming in with the same value. This request is useful for retrieving an overview for parameters that change unfrequently in a large time interval. For example an on/off status of a device, or some operational status. Two consecutive ranges containing the same value will be returned if there was a gap in the data. The gap is determined according to the parameter expiration time configured in the Mission Database.

.. rubric:: Parameters

start (string)
    Filter the lower bound of the parameter's generation time. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the parameter's generation time. Specify a date string in ISO 8601 format.

minGap (long)
    Time in milliseconds. Any gap (detected based on parameter expiration) smaller than this will be ignored. However if the parameter changes value, the ranges will still be split.

maxGap (long)
    Time in milliseconds. If the distance between two subsequent values of the parameter is bigger than this value (but smaller than the parameter expiration), then an artificial gap will be constructed. This also applies if there is no parameter expiration defined for the parameter.

norealtime (bool)
    Disable loading of parameters from the parameter cache. Default: ``false``.

processor (string)
    The name of the processor from which to use the parameter cache. Default: ``realtime``.


.. rubric:: Response
.. code-block:: json

    {
      "range": [{
        "timeStart": "2018-04-06T11:24:01.752Z",
        "timeStop": "2018-04-06T12:21:25.187Z",
        "engValue": {
          "type": "STRING",
          "stringValue": "UNLOCKED"
        },
        "count": 11
      }, {
        "timeStart": "2018-04-06T12:21:25.187Z",
        "timeStop": "2018-04-06T12:26:25.187Z",
        "engValue": {
          "type": "STRING",
          "stringValue": "LOCKED"
        },
        "count": 1
      }]
    }

* ``engValue`` is the engineering value of the parameter in the interval ``[timeStart, timeStop)`` time interval have to be considered as closed at start and open at stop.
* ``timeStart`` is the generation time of a parameter value.
* ``timeStop`` is:
  * if the value changes, ``timeStop`` is the generation time of the new value
  * if the parameter expires or the ``maxGap`` has been set, ``timeStop`` is the generation time of the last value plus the expiration time or the ``maxGap``.
* ``count`` is the number of parameter values received in the interval.


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message Ranges {
       message Range {
            optional string timeStart = 1;
            optional string timeStop = 2;
            optional yamcs.Value engValue = 3;
            optional int32 count = 4;
       }
       repeated Range range = 1;
    }
