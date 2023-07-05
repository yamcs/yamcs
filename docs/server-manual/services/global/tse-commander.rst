TSE Commander
=============

This service allows dispatching commands to Test Support Equipment (TSE). The instrument must have a remote control interface (Serial, TCP/IP) and should support a text-based command protocol such as SCPI.


Class Name
----------

:javadoc:`org.yamcs.tse.TseCommander`


Configuration
-------------

This is a global service defined in :file:`etc/yamcs.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.tse.TseCommander


Configuration Options
---------------------

telnet (map)
    **Required.** Configure Telnet properties.

    Example: ``{ port: 8023 }``

tc (map)
    **Required.** Configure properties of the TC link.

    Example: ``{ port: 8135 }``

tm (map)
    **Required.** Configure properties of the TM link.

    Example: ``{ host: localhost, port: 31002 }``


This service reads further configuration options from a file :file:`etc/tse.yaml`. This file defines all the instruments that can be commanded. Example:

.. code-block:: yaml

    instruments:
      - name: tenma
        class: org.yamcs.tse.SerialPortDriver
        args:
          path: /dev/tty.usbmodem14141
          # Note: this instrument does not terminate responses.
          # Use a very short timeout to compensate (still within spec)
          # responseTermination: "\n"
          responseTimeout: 100

      - name: simulator
        class: org.yamcs.tse.TcpIpDriver
        args:
          host: localhost
          port: 10023
          responseTermination: "\r\n"

      - name: rigol
        class: org.yamcs.tse.TcpIpDriver
        args:
          host: 192.168.88.185
          port: 5555
          responseTermination: "\n"

      - name: udptest
        class: org.yamcs.tse.UdpDriver
        args:
          host: localhost
          port: 5005

There are two types of drivers. Both drivers support these base arguments:

responseTermination (string)
    The character(s) by which the instrument delimits distinct responses. Typically ``\n`` or ``\r\n``. This may be left unspecified if the instrument does not delimit responses.

commandSeparation (string)
    The character(s) that indicates when the command will generate multiple *distinct* responses (delimited by ``responseTermination``). For most instruments this should be left unspecified.

responseTimeout (integer)
    Timeout in milliseconds for a response to arrive. Default: ``3000``

requestTermination (string)
    Character(s) to append to generated string commands. This is typically used for adding newline characters with make the instrument detect a complete request.

    Set this to null if you do not want to disable request termination.

    The default value is driver-specific. For the TCP/IP driver it defaults to ``\n`` whereas for the Serial Port driver, it is unset.

interceptors (list of maps)
    Adds an interceptor chain where each interceptor must be an implementation of :javadoc:`org.yamcs.tse.Interceptor`. Interceptors are executed top-down on these events:
    
    #. A new command is about to be issued. The interceptor can inspect it, or make final changes. The input is in the form of a raw byte array and includes any request termination characters (if applicable).

    #. A non-null response was received. The interceptor can inspect it, or make adjustments before handing it over to the next interceptor. Only at the end of the chain, the response bytes are interpreted by the TSE Commander. Note that the response bytes do **not** include the response termination characters (if any), because the driver already strips them off while delimiting messages from the incoming stream.

    Yamcs ships with one standard interceptor which you can add to an instrument's configuration if you want to enable logging of its command and response messages:

    .. code-block:: yaml

        - name: myinstrument
          class: org.yamcs.tse.TcpIpDriver
          args:
            ...
            interceptors:
              - class: org.yamcs.tse.LoggingInterceptor


In addition each driver supports driver-specific arguments:


TCP/IP
^^^^^^

host (string)
    **Required.** The host of the instrument.

port (integer)
    **Required.** The TCP port to connect to.


UDP
^^^

host (string)
   **Required.** The host of the instrument.

port (integer)
   **Required.** The UDP port to send to.

sourcePort (integer)
   Local sender port. This is also the port where replies can be sent. Default: any available port.

maxLength (integer)
   Buffer size for receiving a single reply. Default: 1500


Serial Port
^^^^^^^^^^^

path (string)
    **Required.** Path to the device.

baudrate (number)
    The baud rate for this serial port. Default: 9600

dataBits (number)
    The number of data bits per word. Default: 8

parity (string)
    The parity error-detection scheme. One of ``odd`` or ``even``. By default parity is not set.


Mission Database
----------------

The definition of TSE string commands is done in space systems resorting under ``/TSE``. The ``/TSE`` node is added by defining :javadoc:`org.yamcs.tse.TseLoader` in the MDB loader tree. Example:

.. code-block:: yaml

    mdb:
      - type: org.yamcs.tse.TseLoader
        subLoaders:
          - type: sheet
            spec: mdb/tse/simulator.xls

The instrument name in :file:`etc/tse.yaml` should match with the name of the a sub space system of /TSE.

The definition of commands and their arguments follows the same approach as non-TSE commands but with some particularities:

* Each command should have either ``QUERY`` or ``COMMAND`` as its parent. These abstract commands are defined by the :javadoc:`org.yamcs.tse.TseLoader`.

  * ``QUERY`` commands send a text command to the remote instrument and expect a text response. The argument assignments ``command`` and ``response`` must both be set to a string template that matches what the instrument expects and returns.

  * ``COMMAND`` commands send a text command to the remote instrument, but no response is expected (or it is simply ignored). Only the argument assignment ``command`` must be set to a string template matching what the instrument expects.

* Each TSE command may define additional arguments needed for the specific command. In the definition of the ``command`` and ``response`` string templates you can refer to the value of these arguments by enclosing the argument name in angle brackets. Example: an argument ``n`` can be dynamically substituted in the string command by referring to it as ``<n>``.

* Additionally you can instruct Yamcs to extract one or more parameter values out of instrument response for a particular command by referring to the parameter name enclosed with backticks. This parameter should be defined in the same space system as the command and use the non-qualified name. The raw type of these parameters should be string.

To illustrate these concepts with an example, consider this query command defined in the space system ``/TSE/simulator``:

.. list-table::
    :header-rows: 1
    :widths: 30 50 20

    * - Command name
      - Assignments
      - Arguments
    * - | get_identification
        | *(parent: QUERY)*
      - | command=*IDN?
        | response=\`identification\`
      -

When issued, this command will send the string ``*IDN?`` to the instrument named ``simulator``. A string response is expected and is read in its entirety as a value of the parameter ``/TSE/simulator/identification``.

The next example shows the definition of a TSE command that uses a dynamic argument in both the command and the response string templates:

.. list-table::
    :header-rows: 1
    :widths: 30 50 20

    * - Command name
      - Assignments
      - Arguments
    * - | get_battery_voltage
        | *(parent: QUERY)*
      - | command=:BATTERY<n>:VOLTAGE?
        | response=\`battery_voltage<n>\`
      - n (range 1-3)

When issued with the argument ``2``, Yamcs will send the string ``:BATTERY2:VOLTAGE?`` to the remote instrument and read back the response into the parameter ``/TSE/simulator/battery_voltage2``. In this simple case you could alternatively have defined three distinct commands without arguments (one for each battery).

.. note::

    When using the option ``commandSeparation``, the ``response`` argument of the command template should use the same separator between the expected responses. For example a query of ``:DATE?;:TIME?`` with command separator ``;`` may be matched in the MDB using the pattern: \`date_param\`;\`time_param\` (regardless of the termination character).


Telnet Interface
----------------

For debugging purposes, this service starts a telnet server that allows to directly relay text-based commands to the configured instruments. This bypasses the TM/TC processing chain. Access this interface with an interactive TCP client such as ``telnet`` or ``netcat``.

The server adds additional SCPI-like commands which allow to switch to any of the configured instruments in a single session. This is best explained via an example:

.. code-block::
    :emphasize-lines: 4,6,9,11,14

    $ nc localhost 8023
    :tse:instrument rigol
    *IDN?
    RIGOL TECHNOLOGIES,DS2302A,DS2D155201382,00.03.00
    :cal:date?;time?
    2018,09,14;21,33,41
    :tse:instrument tenma
    *IDN?
    TENMA72-2540V2.0
    VOUT1?
    00.00
    :tse:output:mode hex
    VOUT1?
    30302E3030

In this session we interacted with two different instruments (named ``rigol`` and ``tenma``). The commands starting with ``:tse`` were directly interpreted by the TSE Commander, everything else was sent to the selected instrument.
