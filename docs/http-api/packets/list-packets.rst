List Packets
============

List the history of packets::

    GET /api/archive/:instance/packets

List the packets for the specified generation time::

    GET /api/archive/:instance/packets/:gentime

The ``:gentime`` must be in ISO 8601 format. E.g. 2015-10-20T06:47:02.000Z


.. rubric:: Parameters

name (array of strings)
    The archived name of the packets. Names must match exactly. Both these notations are accepted:

    * ``?name=/YSS/SIMULATOR/DHS,/YSS/SIMULATOR/Power``
    * ``?name[]=/YSS/SIMULATOR/DHS&name[]=/YSS/SIMULATOR/Power``

start (string)
    Filter the lower bound of the packet's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the packet's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned records per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``desc``


The ``pos`` and ``limit`` allow for pagination. Keep in mind that in-between two requests extra data may have been recorded, causing a shift of the results. This generic stateless operation does not provide a reliable mechanism against that, so address it by overlapping your ``pos`` parameter with rows of the previous query. In this example we overlap by 4:

    ?pos=0&limit=50&order=desc
    ?pos=45&limit=50&order=desc


.. rubric:: Response
.. code-block:: json

    {
      "packet" : [ {
        "receptionTime" : 1447625895283,
        "packet" : "CAEAAABFQ3PHAzxFAAAAIUMCgADCcgxKQlS0OUYh0ADDClmaP9AnUj9yMP1DV9wpQ1fcKT85frs/dofTQwErhUJLPXHCDdLywwpZmg==",
        "generationTime" : 1447625878234,
        "sequenceNumber" : 134283264
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListPacketsResponse {
      repeated yamcs.TmPacketData packet = 1;
    }
