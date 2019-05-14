List Events
===========

List the history of events::

    GET /api/archive/:instance/events/

.. rubric:: Parameters

severity (string)
    The minimum severity level of the events. One of ``info``, ``watch``, ``warning``, ``distress``, ``critical`` or ``severe``. Default: ``info``

q (string)
    Text to search for in the message.

source (array of strings)
    The source of the events. Names must match exactly. Both these notations are accepted:

    * ``?source=DataHandler,CustomAlgorithm``
    * ``?source[]=DataHandler&source[]=CustomAlgorithm``

start (string)
    Filter the lower bound of the event's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the event's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned records per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``desc``

The ``pos`` and ``limit`` allow for pagination. Keep in mind that in-between two requests extra data may have been recorded, causing a shift of the results. This stateless operation does not provide a reliable mechanism against that, so address it by overlapping your ``pos`` parameter with rows of the previous query. In this example we overlap by 4:

    ?pos=0&limit=50&order=desc
    ?pos=45&limit=50&order=desc


.. rubric:: Response
.. code-block:: json

    {
      "event" : [ {
        "source" : "AlarmChecker",
        "generationTime" : 1447425863786,
        "receptionTime" : 1447425863786,
        "seqNumber" : 15,
        "type" : "IN_LIMITS",
        "message" : "Parameter /YSS/SIMULATOR/BatteryVoltage2 has changed to value 222",
        "severity" : "INFO",
        "generationTimeUTC" : "2015-11-13T14:43:47.786Z",
        "receptionTimeUTC" : "2015-11-13T14:43:47.786Z"
      } ]
    }


.. rubric:: Response Schema (Protobuf)
.. code-block:: proto

    message ListEventsResponse {
      repeated yamcs.Event event = 1;
    }


.. rubric:: CSV Output

In order to receive a response in CSV format, use this HTTP request header::

    Accept: text/csv

Or, add this query parameter to the URI: `format=csv`.

.. code-block:: text
    :caption: CSV Output Example

    Source  Generation Time Reception Time  Event Type      Event Text
    AlarmChecker    2015-11-13T14:46:36.029Z 2015-11-13T14:46:36.029Z IN_LIMITS       Parameter /YSS/SIMULATOR/BatteryVoltage2 has changed to value 195
    AlarmChecker    2015-11-13T14:46:29.784Z 2015-11-13T14:46:29.784Z IN_LIMITS       Parameter /YSS/SIMULATOR/BatteryVoltage2 has changed to value 196
    AlarmChecker    2015-11-13T14:46:23.571Z 2015-11-13T14:46:23.571Z IN_LIMITS       Parameter /YSS/SIMULATOR/BatteryVoltage2 has changed to value 197
