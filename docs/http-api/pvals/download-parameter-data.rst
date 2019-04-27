Download Parameter Data
=======================

Download archived parameters::

    GET /api/archive/:instance/downloads/parameters/:namespace/:name


.. rubric:: Parameters

start (string)
    Filter the lower bound of the parameter's generation time. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the parameter's generation time. Specify a date string in ISO 8601 format.

norepeat (bool)
    Whether to filter out consecutive identical values. Default ``no``.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``.


When using CSV output some columns are hidden by default. You can add them via the `extra` flag:

extra (array of strings)
    Extra columns added to the CSV output:

    * ``raw``: Raw parameter values
    * ``monitoring``: Monitoring status

    Example: ``?extra=raw,monitoring``


.. rubric:: Response

The response is a stream of individual parameters.


.. rubric:: Multi-get

Get the value history of multiple parameters in one and the same request using this address::

    GET /api/archive/:instance/downloads/parameters

    .. note::

        POST requests are also allowed, because some HTTP clients do not support GET with a request body.

In addition to the parameters for the single parameter retrieval you can specify these:

namespace (string)
    Namespace used to display parameter names in e.g. csv header. Only used when no parameter ids were specified

ids (list of name pairs)
    Parameters to be included in the output. If not specified, all parameters from the MDB will be dumped.

.. code-block:: json
    :caption: Example JSON Multi-get Request

    {
      "id" : [ {
        "name": "YSS_ccsds-apid",
        "namespace": "MDB:OPS Name"
      }, {
        "name": "/YSS/SIMULATOR/BatteryVoltage2"
      } ]
    }


.. rubric:: Response Schema (protobuf)

The response is a stream of self-standing ``VarInt`` delimited messages of type:

.. code-block:: proto

    message ParameterData {
      repeated ParameterValue parameter = 1;
    }


.. rubric:: Bulk Request Schema (protobuf)
.. code-block:: proto

    message BulkDownloadParameterValueRequest {
      optional string start = 1;
      optional string stop = 2;
      repeated yamcs.NamedObjectId id = 3;
      optional string namespace = 4;
    }


.. rubric:: CSV Output

In order to receive a response in CSV format, use this HTTP request header::

    Accept: text/csv

Or add the query parameter `format=csv`.
