List Parameter Info
===================

List all parameters defined in the Mission Database for the given Yamcs instance::

    GET /api/mdb/:instance/parameters


.. rubric:: Parameters

namespace (string)
    Include parameters under the specified namespace only

recurse (bool)
    If a ``namespace`` is given, specifies whether to list parameters of any nested sub systems. Default ``no``.

type (array of strings)
    The parameter types to be included in the result. Valid types are ``boolean``, ``binary``, ``enumeration``, ``float``, ``integer`` or ``string``. Both these notations are accepted:

    * ``?type=float,integer``
    * ``?type[]=float&type[]=integer``

    If unspecified, parameters of all types will be included.

q (string)
    The search keywords. This supports searching on namespace or name.

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned parameters per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``


.. rubric:: Response
.. code-block:: json

    {
      "parameter" : [ {
        "name": "ccsds-apid",
        "qualifiedName" : "/YSS/ccsds-apid",
        "alias" : [ {
          "name" : "YSS_ccsds-apid",
          "namespace" : "MDB:OPS Name"
        }, {
          "name" : "ccsds-apid",
          "namespace" : "/YSS"
        } ],
        "type" : {
          "engType" : "integer",
          "dataEncoding" : "IntegerDataEncoding(sizeInBits:11, encoding:unsigned, defaultCalibrator:null byteOrder:BIG_ENDIAN)"
        },
        "dataSource" : "TELEMETERED"
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListParameterInfoResponse {
      repeated mdb.ParameterInfo parameter = 1;
    }
