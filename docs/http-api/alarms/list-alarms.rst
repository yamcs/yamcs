List Alarms
===========

List the active alarms for the given processor::

    GET /api/processors/:instance/:processor/alarms

List the history of alarms::

    GET /api/archive/:instance/alarms

List the history of alarms for the given parameter::

    GET /api/archive/:instance/alarms/:namespace/:name

For each alarm the response contains detailed information on the value occurrence that initially triggered the alarm, the most severe value since it originally triggered, and the latest value at the time of your request.


.. rubric:: Parameters

start (string)
    Filter the lower bound of the alarm's trigger time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the alarm's trigger time. Specify a date string in ISO 8601 format. This bound is exclusive.

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned records per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. The sorting is always by trigger time (i.e. the generation time of the trigger value). Default: ``desc``


.. rubric:: Response
.. literalinclude:: _examples/list-alarms-output.json
    :language: json


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListAlarmsResponse {
      repeated alarms.AlarmData alarm = 1;
    }
