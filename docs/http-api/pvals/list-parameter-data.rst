List Parameter Data
===================

List the history of values for the specified parameter::

    GET /api/archive/:instance/parameters/:namespace/:name


.. rubric:: Parameters

start (string)
    Filter the lower bound of the parameter's generation time. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the parameter's generation time. Specify a date string in ISO 8601 format.

norepeat (bool)
    Whether to filter out consecutive identical values. Default ``no``.

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``.

limit (integer)
    The maximum number of returned records per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``desc``.

norealtime (bool)
    Disable loading of parameters from the parameter cache. Default: ``false``.

processor (string)
    The name of the processor from which to use the parameter cache. Default: ``realtime``.

source (string)
    | Specifies how to retrieve the parameters. Either ``ParameterArchive`` or ``replay``. If ``replay`` is specified, a replay processor will be created and data will be processed with the active XTCEDB. Note that this is much slower than receiving data from the ParameterArchive.
    | Default: ``ParameterArchive``.


The ``pos`` and ``limit`` allow for pagination. Keep in mind that in-between two requests extra data may have been recorded, causing a shift of the results. This generic stateless operation does not provide a reliable mechanism against that, so address it by overlapping your ``pos`` parameter with rows of the previous query. In this example we overlap by 4:

    ?pos=0&limit=50&order=desc
    ?pos=45&limit=50&order=desc

When using CSV output some columns are hidden by default. You can add them via the `extra` flag:

extra (array of strings)
    Extra columns added to the CSV output:

    * ``raw``: Raw parameter values
    * ``monitoring``: Monitoring status

    Example: ``?extra=raw,monitoring``


.. rubric:: Response
.. code-block:: json

    {
      "parameter" : [ {
        "id" : {
          "name" : "BatteryVoltage2",
          "namespace" : "/YSS/SIMULATOR"
        },
        "rawValue" : {
          "type" : "UINT32",
          "uint32Value" : 144
        },
        "engValue" : {
          "type" : "UINT32",
          "uint32Value" : 144
        },
        "acquisitionTime" : 1447417449218,
        "generationTime" : 1447417432121,
        "acquisitionStatus" : "ACQUIRED",
        "processingStatus" : true,
        "monitoringResult" : "IN_LIMITS",
        "acquisitionTimeUTC" : "2015-11-13T12:23:33.218Z",
        "generationTimeUTC" : "2015-11-13T12:23:16.121Z",
        "expirationTime" : 1447417456218,
        "expirationTimeUTC" : "2015-11-13T12:23:40.218Z",
        "alarmRange" : [ {
          "level" : "WATCH",
          "minInclusive" : 50.0
        }, {
          "level" : "WARNING",
          "minInclusive" : 40.0
        }, {
          "level" : "DISTRESS",
          "minInclusive" : 30.0
        }, {
          "level" : "CRITICAL",
          "minInclusive" : 20.0
        }, {
          "level" : "SEVERE",
          "minInclusive" : 10.0
        } ]
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ParameterData {
      repeated ParameterValue parameter = 1;
    }


.. rubric:: CSV Output

In order to receive a response in CSV format, use this HTTP request header::

    Accept: text/csv

Or add this query parameter to the URI: `format=csv`.

.. code-block:: text
    :caption: CSV Output Example

    Time    BatteryVoltage2
    2015-11-13T12:21:55.199 157
    2015-11-13T12:21:48.972 158
    2015-11-13T12:21:42.750 159
