Download Events
===============

Download archived events::

    GET /api/archive/:instance/downloads/events

.. note::

    This operation will possibly download a very large file. If you worry about size for your application, check out the support for `paged event retrievals <list-events>`_ instead.


.. rubric:: Parameters

severity (string)
    The minimum severity level of the events. One of ``info``, ``watch``, ``warning``, ``distress`` or ``severe``. Default: ``info``

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

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``


.. rubric:: Response

The response is a stream of self-standing event records.


.. rubric:: Response Schema (protobuf)

The response is a stream of individual Protobuf messages delimited by a ``VarInt``. Messages are of type:

.. code-block:: proto

    message Event {
      enum EventSeverity {
        INFO = 0;
        WARNING = 1;
        ERROR = 2;
      }
      required string source = 1;
      required int64 generationTime = 2;
      required int64 receptionTime = 3;
      required int32 seqNumber = 4;
      optional string type = 5;
      required string message = 6;
      optional EventSeverity severity = 7[default=INFO];

      optional string generationTimeUTC = 8;
      optional string receptionTimeUTC = 9;

      extensions 100 to 10000;
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
