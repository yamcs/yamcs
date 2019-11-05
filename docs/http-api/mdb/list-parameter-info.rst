List Parameter Info
===================

List all parameters defined in the Mission Database for the given Yamcs instance::

    GET /api/mdb/:instance/parameters


.. rubric:: Parameters

type (array of strings)
    The parameter types to be included in the result. Valid types are ``boolean``, ``binary``, ``enumeration``, ``float``, ``integer`` or ``string``. Both these notations are accepted:

    * ``?type=float,integer``
    * ``?type=float&type=integer``

    If unspecified, parameters of all types will be included.

q (string)
    The search keywords. This supports searching on namespace or name.

system (string)
    List only direct child sub-systems or parameters of the specified system. For example when querying the system "/a" against an MDB with parameters "/a/b/c" and "/a/c", the result returns the sub system "/a/b" and the parameter "/a/c".

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned parameters per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

When ``system`` and ``q`` are used together, matching parameters at any depth are returned, starting from the specified space system.


.. rubric:: Response
.. code-block:: json

    {
      "parameters" : [ {
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

    message ListParametersResponse {
      repeated mdb.ParameterInfo parameters = 1;
    }
