List Command
============

List the command history of all commands::

    GET /api/archive/:instance/commands

List the command history of one specific command::

    GET /api/archive/:instance/commands/:namespace/:name

.. rubric:: Parameters

start (string)
    Filter the lower bound of the command's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the command's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned records per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``desc``

The ``pos`` and ``limit`` allow for pagination. Keep in mind that in-between two requests extra data may have been recorded, causing a shift of the results. This stateless operation does not provide a reliable mechanism against that, so address it by overlapping your ``pos`` parameter with rows of the previous query. In this example we overlap by 4:

    ?pos=0&limit=50&order=desc
    ?pos=45&limit=50&order=desc

An alternative is to download the command history instead.


.. rubric:: Response
.. literalinclude:: _examples/list-commands-output.json
    :language: json


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListCommandsResponse {
      repeated commanding.CommandHistoryEntry entry = 1;
    }
