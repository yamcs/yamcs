PUS Parameter Manager
=====================

Makes the on-board parameters managed by the PUS ST[20] parameter management service editable from Yamcs, for example with the edit button of the web interface.

The on-board parameters are regular MDB parameters. They have no value until one is received in a TM[20,2] parameter value report, which is the response to a TC[20,1] "report parameter values" command sent via the regular commanding flow.

When the value of such a parameter is edited, this service sends a TC[20,3] "set parameter values" command. The effect is the same as when the user sends the command from the commanding page: the user needs the privilege for the command, and the command goes through the same command queues, verification and command history, attributed to the user. By default, the service then also sends a TC[20,1] "report parameter values" command for the updated parameters, so that the new values are shown right away. This can be disabled with the ``reportAfterSet`` option. The report is sent as a separate command: it is not delayed until the TC[20,3] completes, so if the command queues or the on-board processing reorder them, the report may show the old values. The user needs the privilege for the report command as well; without it only the TC[20,3] is sent. A failure to send the report is logged and does not make the update fail.

All the values of one update are sent in one TC[20,3]. The value of each parameter is encoded according to the data encoding of the parameter, which defines the size of the value in the message. Only parameters without calibration and with integer, float, enumerated or boolean types are supported.

The messages for the management of parameter definitions (TC[20,4], TC[20,5], TC[20,6] and TM[20,7]) are not supported.


Class Name
----------

:javadoc:`org.yamcs.pus.PusParameterManager`


Configuration
-------------

This service is defined in :file:`etc/processor.yaml`. Example:

.. code-block:: yaml

    realtime:
      services:
        - class: org.yamcs.pus.PusParameterManager

The on-board parameters must have the data source ``EXTERNAL1`` (configurable, see below) and an alias in the configured namespace. The alias is the name of the ST[20] parameter ID, as defined in the enumeration of the parameter ID mapping (default ``/PUS20/parameter_id``, configurable). The same alias is used by the MDB to decode the TM[20,2] reports.

The report command is a MDB command with the arguments ``num_parameters`` (the number of parameters) and ``parameter_ids`` (an array of parameter ID enumeration labels).

The set command is a MDB command with one binary argument ``instructions``. The argument contains the application data of TC[20,3]: the number of instructions N followed by N times a parameter ID and the value.


Configuration Options
---------------------

dataSource (string)
    The data source of the managed parameters. Has to be one that can be edited from the web interface: ``EXTERNAL1``, ``EXTERNAL2`` or ``EXTERNAL3``. Default: ``EXTERNAL1``.

aliasNamespace (string)
    The alias namespace of the on-board parameters containing the name of the parameter ID. Default: ``PUS20_Parameter_ID``.

parameterIdMapping (string)
    The enumerated parameter that maps the names to the the ST[20] parameter IDs. Default: ``/PUS20/parameter_id``.

setCommand (string)
    The fully qualified name of the TC[20,3] command. Default: ``/PUS20/SET_PARAMETER_VALUES``.

reportAfterSet (boolean)
    Send a TC[20,1] to report the values of the updated parameters after sending the TC[20,3]. Default: ``true``.

reportCommand (string)
    The fully qualified name of the TC[20,1] command. Only used if ``reportAfterSet`` is true. Default: ``/PUS20/REPORT_PARAMETER_VALUES``.

parameterIdBits (integer)
    Size in bits of the parameter ID in TC[20,3]. Default: 8.

numberOfInstructionsBits (integer)
    Size in bits of the number of instructions (N) in TC[20,3]. Default: 8.