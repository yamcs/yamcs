Get Parameter Samples
=====================

Sample the history of values for the specified parameter by dividing it in a number of intervals and returning aggregated statistics (max,min,avg) about each interval::

    GET /api/archive/:instance/parameters/:namespace/:name/samples

This operation is useful when making high-level overviews (such as plots) of a parameter's value over large time intervals without having to retrieve each and every individual parameter value.

.. note::

    By default this operation fetches data from the parameter archive and/or parameter cache. If these services are not configured, you can still get correct results by specifying the option ``source=replay`` as detailed below.


.. rubric:: Parameters

start (string)
    Filter the lower bound of the parameter's generation time. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the parameter's generation time. Specify a date string in ISO 8601 format.

count (integer)
    Number of intervals to use. Default: ``500``.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``.

norealtime (boolean)
    Disable loading of parameters from the parameter cache. Default: ``false``.

processor (string)
    The name of the processor from which to use the parameter cache. Default: ``realtime``.

source (string)
    | Specifies how to retrieve the parameters. Either ``ParameterArchive`` or ``replay``. If ``replay`` is specified, a replay processor will be created and data will be processed with the active XTCEDB. Note that this is much slower than receiving data from the ParameterArchive.
    | Default: ``ParameterArchive``.


.. rubric:: Response
.. code-block:: json

    {
      "sample" : [ {
        "time" : "2015-11-11T09:11:37.626",
        "avg" : 169.41836734693865,
        "min" : 103.0,
        "max" : 237.0,
        "n" : 98
      } ]
    }


.. rubric:: Response Schema
.. code-block:: proto

    message TimeSeries {
      message Sample {
        optional string time = 1;
        optional double avg = 2;
        optional double min = 3;
        optional double max = 4;
        optional int32 n = 5;
      }

      repeated Sample sample = 1;
    }
