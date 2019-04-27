Spreadsheet Loader
==================

The spreadsheet loader loads mission database definitions from excel spreadsheet. Only excel prior to Excel 2007 are supported (.xls files not .xlsx).


Multiple Space Systems support
------------------------------

Since version 5.4, the spreadsheet definition supports loading from one Excel file a hierarchy composed of multiple space systems. Until version 5.3 this was only possible by defining multiple Excel files (one per subsystem) and defining the hierarchy in ``etc/mdb.yaml``. Also until version 5.3 the loader forced some sheets to always be present (e.g. Containers). From version 5.4 only the General sheet has to be present, all the other ones are optional.

To define the space system hierarchy, the convention is that all the sheets that do not have a prefix contain data for the main space system whose name is defined in the General sheet.
To define data in subsystems, a syntax like ``SYSTEM1|SYSTEM2|Containers`` can be used. This definition will create a SYSTEM1 as part of the main space system and a child SYSTEM2 of SYSTEM1. Then the containers will be loaded in SYSTEM2.

The spreadsheet loader scans and creates the subsystem hierarchy and then it loads the data inside the systems traversing the hierarchy in a depht-first order.


Conventions
-----------

* All numeric values can be entered as decimals or as hexadecimals (with prefix ``0x``)
* Although column names are used for reference below, columns must not be reordered

A number of mandatory named sheets are described as part of this specification, though authors may add their own sheets and still use the spreadsheet file as the reference MDB.


Rules for parameter/container reference lookup
----------------------------------------------

Each time a name reference is mentioned in the spreadsheet, the following rules apply:

* The reference can use UNIX like directory access expressions (../a/b).
* If the name is not found as a qualified parameter, and the option enableAliasReferences is configured for the SpreadsheetLoader, the parameter is looked up through all the aliases of the parent systems.

The result of the lookup depends on the exact tree configuration in mdb.yaml


General Sheet
-------------
This sheet must be named "General", and the columns described must not be reordered.

format version
    Used by the loader to ensure a compatible spreadsheet structure

name
    Name of the MDB

document version
    Used by the author to track versions in an arbitrary manner


Containers Sheet
----------------

This sheet must be named *Containers*, and the columns described must not be reordered. The sheet contains description of the content of the container (packet).
As per XTCE, a container is a structure describing a binary chunk of data composed of multiple entries.

A container can inherit from other container - meaning that it takes all entries from the parent and it adds some more. It can have two types of entries:

* parameters
* other containers (this is called aggregation)

General conventions:

* first line with a new 'container name' starts a new packet
* second line after a new 'container name' should contain the first measurement
* empty lines are only allowed between two packets

Comment lines starting with ``#`` on the first column can appear everywhere and are ignored.

container name
        The relative name of the packet inside the space system

parent
        Parent container and position in bits where the subcontainer starts, for example  ``PARENT_CONTAINER:64``. If position in bits is not specified, the default position is to start from the last parameter in the parent. If parent is not specified, either the container is the root, or it can be used as part of another container in aggregation.

condition
        Inheritance condition, usually specifies a switch within the parent which activates this child, for example `MID=0x101` There are currently three forms supported:

        * Simple condition:  ``Parameter==value``
        * Condition list:  ``Parameter==value;Parameter2==value2`` - all conditions must be true
        * Boolean condition: ``op(epx1;exp2;...;expn)``
            * op is '&' (AND) or '|' (OR)
            * expi is a boolean expression or a simple condition

        Currently the only supported conditions are on the parameters of the parent container. This cover the usual case where the parent defines a header and the inheritance condition is based on paraemters from the header.


Parameters Sheet
----------------
This sheet must be named ending with *Parameters*, and the columns described must not be reordered. The sheet contains parameter (sometimes called measurements) information.
Any number of sheets ending with *Parameters* can be present and they all have the same structure. Each parameter has a so called *DataSource* (as per XTCE) which is not immediately configured.
However by historical convention:

* DerivedParameters contains all parameter whose data source is set to "DERIVED" - these are usually results of algorithm computations.
* LocalParameters contains all parameters whose data source is set to "LOCAL" - these are parameters that can be set by the user using the Yamcs API.
* All other parameter sheets contain parameters whose data source is set to "TELEMETERED" - these are parameters received from remote devices.

A parameter when extracted from a binary packet has two forms: a raw value and an engineering value. The extraction from the raw packet is performed according to the encoding, whereas the conversion from raw to engineering value is performed by a calibrator. This sheet can also be used to specify parameters without encoding - if they are received already extracted, Yamcs can do only their calibration. Or it can be that a parameter is already calibrated, it can still be specified here to be able to associate alarms.

Empty lines can appear everywhere and are ignored. Comment lines starting with ``#`` on the first column can appear everywhere and are ignored.

name
        The name of the parameter in the namespace.

encoding
        Description on how to extract the raw type from the binary packet. See below for all supported encodings.

raw type
        Documented below.

eng type
        Documented below.

eng unit
        Free-form textual description of unit(s). E.g. degC, W, V, A, s, us

calibration
        Name of a calibration described in the Calibration sheet, leave empty if no calibration is applied

description
        Optional human-readable text

namespace:<NS-NAME>
        If present, these columns can be used to assign additional names to the parameters in the namespace NS-NAME. Any number of columns can be present to give additional names in different namespaces.


Encoding and Raw Types
^^^^^^^^^^^^^^^^^^^^^^

The raw type and encoding describe how the parameter is encoded in the binary packet. All types are case-insensitive.

Unsigned Integers
"""""""""""""""""

Raw type: ``uint``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``unsigned(<n>,<BE|LE>)``
      - unsigned integer
    * - ``<n>``
      - shortcut for ``unsigned(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Signed Integers
"""""""""""""""

Raw type: ``int``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``twosComplement(<n>,<BE|LE>)``
      - two's complement encoding
    * - ``signMagnitude(<n>,<BE|LE>)``
      - sign magnitude encoding - first (or last for LE) bit is the sign, the remaining bits represent the magnitude (absolute value).
    * - ``<n>``
      - shortcut for ``twosComplement(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Floats
""""""

Raw type: ``float``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``ieee754_1985(<n>,<BE|LE>)``
      - IEE754_1985 encoding
    * - ``<n>``
      - shortcut for ``ieee754_1985(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Booleans
""""""""

Raw type: ``boolean``

Encoding: Leave empty. 1 bit is assumed.


String
""""""

Raw type: ``string``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``fixed(<n>, <charset>)``
      - fixed size string. The string has to start at a byte boundary inside the container.
    * - ``PrependedSize(<x>, <charset>)``
      - string whose length in bytes is specified by the first ``x`` bits of the array
    * - ``<n>``
      - shortcut for ``fixed(<n>)``
    * - ``terminated(<0xBB>, <charset><m>)``
      - terminated string

Where:

``n`` is the size in bits. Only multiples of 8 are supported.

``x`` is the size in bits of the size tag. Only multiples of 8 are supported. The size must be expressed in bytes.

``charset`` is one of the `charsets supported by java <https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html>`_ (UTF-8, ISO-8859-1, etc). Default: UTF-8.

``0xBB`` specifies a byte that is the string terminator. Pay attention to the parameters following this one; if the terminator is not found the entire buffer will be consumed.


Binary
""""""

Raw type: ``binary``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``fixed(<n>)``
      - fized size byte array
    * - ``PrependedSize(<x>)``
      - byte array whose size in bytes is specified in the first ``x`` bits of the array
    * - ``<n>``
      - shortcut for ``fixed(<n>)``

Where:

``n`` is the size in bits. Only multiples of 8 are supported and it has to start at a byte boundary.

``x`` is the size in bits of the size tag. Note that while ``x`` can be any number <= 32, the byte array has to start at a byte boundary.


Custom
""""""

Raw type: *any*

Encoding: ``custom(<n>,algorithm)``

The decoding will be performed by a user defined algorithm.

* ``<n>`` is optional and may be used to specify the size in bits of the entry in the container (in case the size is fixed) - it is used for optimizing the access to the parameters following this one.
* ``algorithm`` the name of the algorithm - it has to be defined in the *Algorithms* sheet


Engineering Types
^^^^^^^^^^^^^^^^^

Engineering types describe a parameter in its processed form (i.e. after any calibrations). All types are case-insensitive.

Depending on the combination of raw and engineering type, automatic conversion is applicable. For more advanced use cases, define and refer to a Calibrator in the Calibration Sheet

.. list-table::
    :widths: 20 40 40
    :header-rows: 1

    * - Type
      - Description
      - Automatic Conversion
    * - uint
      - Unsigned 32 bit integer - it corresponds to ``int`` in java and ``uint32`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - uint64
      - Unsigned 64 bit integer - it corresponds to ``long`` in java and ``uint64`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - int
      - Signed 32 bit integer - it corresponds to ``int`` in java and ``int32`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - int64
      - Signed 64 bit integer - it corresponds to ``long`` in java and ``int64`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - string
      - Character string - it corresponds to ``String`` in java and ``string`` in protobuf
      - From ``string``
    * - float
      - 32 bit floating point number - it corresponds to ``float`` in java and protobuf
      - From ``float``, ``int``, ``uint`` or ``string``
    * - double
      - 64 bit floating point number - it corresponds to ``double`` in java and protobuf
      - From ``float``, ``int``, ``uint`` or ``string``
    * - enumerated
      - A kind of string that can only be one out of a fixed set of predefined state values. It corresponds to ``String`` in java and ``string`` in protobuf.
      - From ``int`` or ``uint``. A Calibrator is required.
    * - boolean
      - A binary true/false value - it corresponds to 'boolean' in java and 'bool' in protobuf
      - From any raw type. Values equal to zero, all-zero bytes or an empty string are considered *false*.
    * - binary
      - Byte array - it corresponds to ``byte[]`` in java and ``bytes`` in protobuf.
      - From ``bytestream`` only


Calibration Sheet
-----------------

This sheet must be named *Calibration*, and the columns described must not be reordered. The sheet contains calibration data including enumerations.

calibrator name
    Name of the calibration - it has to match the calibration column in the Parameter sheet.

type
    One of the following:

    * ``polynomial`` for polynomial calibration.  Note that the polynomial calibration is performed with double precision floating point numbers even though the input and/or output may be 32 bit.
    * ``spline`` for linear spline (pointpair) interpolation. As for the polynomial, the computation is performed with double precision numbers.
    * ``enumeration`` for mapping enumeration states.
    * ``java-expression`` for writing more complex functions.

calib1
    * If the type is ``polynomial``: it list the coefficients, one per row starting with the constant and up to the highest grade. There is no limit in the number of coefficients (i.e. order of polynomial).
    * If the type is ``spline``: start point (x from (x,y) pair)
    * If the type is ``enumeration``: numeric value
    * If the type is ``java-expression``: the textual formula to be executed (see below)

calib2
    * If the type is ``polynomial``: leave *empty*
    * If the type is ``spline``: stop point (y) corresponding to the start point(x) in ``calib1``
    * If the type is ``enumeration``: text state corresponding to the numeric value in ``calib1``
    * If the type is ``java-expression``: leave *empty*


Java Expressions
^^^^^^^^^^^^^^^^

This is intended as a catch-all case. XTCE specifies a MathOperationCalibration calibrator that is not implemented in Yamcs. However these expressions can be used for the same purpose.

They can be used for float or integer calibrations.

The expression appearing in the `calib1` column will be enclosed and compiled into a class like this:

.. code-block:: java

    package org.yamcs.xtceproc.jecf;
    public class Expression665372494 implements org.yamcs.xtceproc.CalibratorProc {
        public double calibrate(double rv) {
                return <expression>;
        }
    }


The expression has usually to return a double; but java will convert implicitly any other primitive type to a double.

Java statements cannot be used but the conditional operator ``? :`` can be used; for example this expression would compile fine:

.. code-block:: java

    rv>0?rv+5:rv-5


Static functions can be also referenced. In addition to the usual Java ones (e.g. Math.sin, Math.log, etc) user own functions (that can be found as part of a jar on the server in the lib/ext directory) can be referenced by specifying the full class name:

.. code-block:: java

    my.very.complicated.calibrator.Execute(rv)


Algorithms Sheet
----------------

This sheet must be named "Algorithms", and the columns described must not be reordered. The sheet contains arbitrarily complex user algorithms that can set (derived) output parameters based on any number of input parameters.

Comment lines starting with “#” on the first column can appear everywhere and are ignored.
Empty lines are used to separate algorithms and cannot be used inside the specification of one algorithm.


algorithm name
    The identifying name of the algorithm.

algorithm language
    The programming language of the algorithm. Currently supported values are:

    * JavaScript
    * python - note that this requires the presence of jython.jar in the Yamcs lib or lib/ext directory (it is not delivered together with Yamcs)
    * Java

text
    The code of the algorithm (see below for how this is interpreted).

trigger
    Optionally specify when the algorithm should trigger:

    * ``OnParameterUpdate('/some-param', 'some-other-param')`` Execute the algorithm whenever *any* of the specified parameters are updated
    * ``OnInputParameterUpdate`` This is the same as above for all input parameters (i.e. execute whenever *any* input parameter is updated).
    * ``OnPeriodicRate(<fireRate>)`` Execute the algorithm every ``fireRate`` milliseconds
    * ``none`` The algorithm doesn't trigger automatically but can be called upon from other parts of the system (like the command verifier)

    The default is none.

in/out
    Whether a parameter is inputted to, or outputted from the algorithm. Parameters are defined, one per line, following the line defining the algorithm name

parameter reference
    Reference name of a parameter. See above on how this reference is resolved.

    Algorithms can be interdependent, meaning that the output parameters of one algorithm could be used as input parameters of another algorithm.

instance
    Allows inputting a specific instance of a parameter. At this stage, only values smaller than or equal to zero are allowed. A negative value, means going back in time. Zero is the default and means the actual value. This functionality allows for time-based window operations over multiple packets. Algorithms with windowed parameters will only trigger as soon as all of those parameters have all instances defined (i.e. when the windows are full).

    Note that this column should be left empty for output parameters.

name used in the algorithm
    An optional friendlier name for use in the algorithm. By default the parameter name is used, which may lead to runtime errors depending on the naming conventions of the applicable script language.

    Note that a unique name is required in this column, when multiple instances of the same parameter are inputted.


JavaScript algorithms
^^^^^^^^^^^^^^^^^^^^^

A full function body is expected. The body will be encapsulated in a javascript function like:

.. code-block:: javascript

    function algorithm_name(in_1, in_2, ..., out_1, out_2...) {
        <algorithm-text>
    }

The ``in_n`` and ``outX`` are to be names given in the spreadsheet column *name used in the algorithm*.

The method can make use of the input variables and assign out_x.value (this is the engineering value) or out_x.rawValue (this is the raw value) and out_x.updated for each output variable.
The <out>.updated can be set to false to indicate that the output value has not to be further processed even if the algorithm has run.
By default it is true - meaning that each time the algorithm is run, it is assumed that it updates all the output variables.

If out_x.rawValue is set and out_x.value is not, then Yamcs will run a calibration to compute the engineering value.

Note that for some algorithms (e.g. command verifiers) need to return a value.


Python algorithms
^^^^^^^^^^^^^^^^^

This works very similarly with the JavaScript algorithms, The thing to pay attention is the indentation. The algorithm text wihch is specified in the spreadsheet will be automatically indented with 4 characters:

.. code-block:: python

    function algorithm_name(in_1, in_2, ..., out_1, out_2...) {
        <algorithm-text>
    }


Java algorithms
^^^^^^^^^^^^^^^

The algorithm text  is a class name with optionally parantheses enclosed string that is parsed into an object by a yaml parser.
Yamcs will try to locate the given class who must be implementing the org.yamcs.algorithms.AlgorithmExecutor interface and will create an object with a constructor with three parameters:

.. code-block:: java

    MyAlgorithmExecutor(Algorithm, AlgorithmExecutionContext, Object arg)


where ``arg`` is the argument parsed from the yaml.

If the optional argument is not present in the algorithm text definition,  then the class constructor  should only have two parameters.
The abstract class ``org.yamcs.algorithms.AbstractAlgorithmExecutor`` offers some helper methods and can be used as base class for implementation of such algorithm.

If the algorithm is used for data decoding, it has to implement the ``org.yamcs.xtceproc.DataDecoder`` interface instead (see below).


Command verifier algorithms
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Command verifier algorithms are special algorithms associated to the command verifiers. Multiple instances of the same algorithm may execute in parallel if there are multiple pending commands executed in parallel.

These algorithms are special as they can use as input variables not only parameters but also command arguments and command history events. These are specified by using "/yamcs/cmd/arg/" and "/yamcs/cmdHist" prefix respectively.

In addition these algorithms may return a boolean value (whereas the normal algorithms only have to write to output variables). The returned value is used to indicate if the verifier has succeeded or failed. No return value will mean that the verifier is still pending.


Data Decoding algorithms
^^^^^^^^^^^^^^^^^^^^^^^^

The Data Decoding algorithms are used to extract a raw value from a binary buffer. These algorithms do not produce any output and are triggered whenever the parameter has to be extracted from a container.

These algorithms work differently from the other ones and have are some limitations:

* only Java is supported as a language
* not possible to specify input parameters

These algorithms have to implement the interface org.yamcs.xtceproc.DataDecoder.


Alarms Sheet
------------

This sheet must be named *Alarms*, and the columns described must not be reordered. The sheet defines how the monitoring results of a parameter should be derived. E.g. if a parameter exceeds some pre-defined value, this parameter's state changes to ``CRITICAL``.

parameter name
    The reference name of the parameter for which this alarm definition applies

context
    A condition under which the defined triggers apply. This can be used to define multiple different sets of triggers for one and the same parameter, that apply depending on some other condition (typically a state of some kind). When left blank, the defined set of conditions are assumed to be part of the *default* context.

    Contextual alarms are evaluated from top to bottom, until a match is found. If no context conditions apply, the default context applies.

report
    When alarms under the given context should be reported. Should be one of ``OnSeverityChange`` or ``OnValueChange``. With ``OnSeverityChange`` being the default. The condition ``OnValueChange`` will check value changes based on the engineering values. It can also be applied to a parameter without any defined severity levels, in which case an event will be generated with every change in value.

minimum violations
    Number of successive instances that meet any of the alarm conditions under the given context before the alarm event triggers (defaults to 1). This field affects when an event is generated (i.e. only after X violations). It does not affect the monitoring result associated with each parameter. That would still be out of limits, even after a first violation.

watch: trigger type
    One of ``low``, ``high`` or ``state``. For each context of a numeric parameter, you can have both a low and a high trigger that lead to the ``WATCH`` state. For each context of an enumerated parameter, you can have multiple state triggers that lead to the ``WATCH`` state.

watch: trigger value
    If the trigger type is ``low`` or ``high``: a numeric value indicating the low resp. high limit value. The value is considered inclusive with respect to its nominal range. For example, a low limit of 20, will have a ``WATCH`` alarm if and only if its value is smaller than 20.

    If the trigger value is ``state``: a state that would bring the given parameter in its ``WATCH`` state.

warning: trigger type, warning: trigger value
    Analogous to ``watch`` condition

distress: trigger type, distress: trigger value
    Analogous to ``watch`` condition

critical: trigger type, critical: trigger value
    Analogous to ``watch`` condition

severe: trigger type, severe: trigger value
    Analogous to ``watch`` condition


Commands Sheet
--------------

This sheet must be named *Commands*, and the columns described must not be reordered.
The sheet contains commands description, including arguments. General convention:

* First line with a new 'Command name' starts a new command
* Second line after a new 'Command name' should contain the first command arguments
* Empty lines are only allowed between two commands.

Command name
    The name of the command. Any entry starting with `#` is treated as a comment row

parent
    name of the parent command if any.

    Can be specified starting with / for an absolute reference or with ../ for pointing to parent SpaceSystem :x means that the arguments in this container start at position x (in bits) relative to the topmost container. Currently there is a problem for containers that have no argument: the bit position does not apply to children and has to be repeated.

argAssignment
    name1=value1;name2=value2.. where name1,name2.. are the names of arguments which are assigned when the inheritance takes place

flags
    For commands: A=abstract. For arguments: L = little endian

argument name
    From this column on, most of the cells are valid for arguments only. These have to be defined on a new row after the command. The exceptions are: description, aliases

relpos
    Relative position to the previous argument. Default: 0

encoding
    How to convert the raw value to binary. The supported encodings are listed in the table below.

eng type
    Engineering type; can be one of: uint, int, float, string, binary, enumerated, boolean or FixedValue.
    FixedValue is like binary but is not considered an argument but just a value to fill in the packet.

raw type
    Raw type: one of the types defined in the table below.

(default) value
    Default value. If eng type is FixedValue, this has to contain the value in hexadecimal. Note that when the size of the argument is not an integer number of bytes (which is how hexadecimal binary strings are specified), the most significant bits are ignored.

eng unit

calibration
    Point to a calibration from the Calibration sheet

range low
    The value of the argument cannot be smaller than this. For strings and binary arguments this means the minimum length in characters, respectively bytes.

range high
    The value of the argument cannot be higher than this. Only applies to numbers. For strings and binary arguments this means the minimum length in characters, respectively bytes.

description
    Optional free text description


Encoding and Raw Types for command arguments
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The raw type and encoding describe how the argument is encoded in the binary packet. All types are case-insensitive.

Unsigned Integers
"""""""""""""""""

Raw type: ``uint``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``unsigned(<n>,<BE|LE>)``
      - unsigned integer
    * - ``<n>``
      - shortcut for ``unsigned(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Signed Integers
"""""""""""""""

Raw type: ``int``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``twosComplement(<n>, <BE|LE>)``
      - two's complement encoding
    * - ``signMagnitude(<n>,<BE|LE>)``
      - sign magnitude encoding - first (or last for LE) bit is the sign, the remaining bits represent the magnitude (absolute value).
    * - ``<n>``
      - shortcut for ``twosComplement(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Floats
""""""

Raw type: ``float``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``ieee754_1985(<n>,<BE|LE>)``
      - IEE754_1985 encoding
    * - ``<n>``
      - shortcut for ``ieee754_1985(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Booleans
""""""""

Raw type: ``boolean``

Encoding: Leave empty. 1 bit is assumed.


String
""""""

Raw type: ``string``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``fixed(<n>, <charset>)``
      - fixed size string
    * - ``PrependedSize(<x>, <charset><m>)``
      - string whose length in bytes is specified by the first ``x`` bits of the array
    * - ``<n>``
      - shortcut for ``fixed(<n>)``
    * - ``terminated(<0xBB>, <charset><m>)``
      - terminated string

Where:

``n`` is the size in bits. Only multiples of 8 are supported.

``x`` is the size in bits of the size tag. Only multiples of 8 are supported. The size must be expressed in bytes.

``charset`` is one of the `charsets supported by java <https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html>`_ (UTF-8, ISO-8859-1, etc). Default: UTF-8.

``m`` if specified, it is the minimum size in bits of the encoded value. Note that the size reflects the real size of the string even if smaller than this minimum size. This option has been added for compatibility with the Airbus CGS system but its usage is discouraged since it is not compliant with XTCE.

``0xBB`` specifies a byte that is the string terminator.


Binary
""""""

Raw type: ``binary``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``fixed(<n>)``
      - fized size byte array
    * - ``PrependedSize(<x>)``
      - byte array whose size in bytes is specified in the first ``x`` bits of the array
    * - ``<n>``
      - shortcut for ``fixed(<n>)``

Where:

``n`` is the size in bits. Only multiples of 8 are supported and it has to start at a byte boundary.

``x`` is the size in bits of the size tag. Note that while ``x`` can be any number <= 32, the byte array has to start at a byte boundary.


Command Options Sheet
---------------------

This sheet must be named *CommandOptions*, and the columns described must not be reordered.
This sheet defines two types of options for commands:

* transmission constraints - these are conditions that have to be met in order for the command to be sent.
* command significance - this is meant to flag commands that have a certain significance. Currently the significance is only used by the end user applications (e.g. Yamcs Studio) to raise the awarness of the operator when sending such command.

Command name
    The name of the command. Any entry starting with ``#`` is treated as a comment row

Transmission Constraints
    Constrains can be specified on multiple lines. All of them have to be met for the command to be allowed for transmission.

Constraint Timeout
    This refers to the left column. A command stays in the queue for that many milliseconds. If the constraint is not met, the command is rejected. 0 means that the command is rejected even before being added to the queue, if the constraint is not met.

Command Significance
    Significance level for commands. Depending on the configuration, an extra confirmation or certain privileges may be required to send commands of high significance. One of:

    - none
    - watch
    - warning
    - distress
    - critical
    - severe

Significance Reason
    A message that will be presented to the user explaining why the command is significant.


Command Verification Sheet
--------------------------

The Command verification sheets defines how a command shall be verified once it has been sent for execution.

The transmission/execution of a command usual goes through multiple stages and a verifier can be associated to each stage.
Each verifier runs within a defined time window which can be relative to the release of the command or to the completion of the previous verifier. The verifiers have three possible outcomes:

    * OK = the stage has been passed successfully.
    * NOK = the stage verification has failed (for example there was an error on-board when executing the command, or the uplink was not activated).
    * timeout - the condition could not be verified within the defined time interval.

For each verifier it has to be defined what happens for each of the three outputs.

Command name
    The command relative name as defined in the Command sheet. Referencing commands from other subsystems is not supported.

CmdVerifier Stage
    Any name for a stage is accepted but XTCE defines the following ones:

    * TransferredToRange
    * SentFromRange
    * Received
    * Accepted
    * Queued
    * Execution
    * Complete
    * Failed

    Yamcs interprets these as strings without any special semantics. If special actions (like declaring the command as completed) are required for Complete or Failed, they have to be configured in OnuSccess/OnFail/OnTimeout columns. By default command history events with the name Verification_<stage> are generated.

CmdVerifier Type
    Supported types are:

    * container – the command is considered verified when the container is received. Note that this cannot generate a Fail (NOK) condition - it's either OK if the container is received in the timewindow or timeout if the container is not received.
    * algorithm – the result of the algorithm run is used as the output of the verifier. If the algorithm is not run (because it gets no inputs) or returns null, then the timeout condition applies

CmdVerifier Text
    Depending on the type:

    * container: is the name of the container from the Containers sheet. Reference to containers from other space systems is not supported.
    * algorithm: is the name of the algorithm from the Algorithms sheet. Reference to algorithms from other space systems is not supported.

Time Check Window
    start,stop in milliseconds defines when the verifier starts checking the command and when it stops.

checkWindow is relative to
    * LastVerifier (default) – the start,stop in the window definition are relative to the end of the previous verifier. If there is no previous verifier, the start,stop are relative to the command release time. If the previous verifier ends with timeout, this verifier will also timeout without checking anything.
    * CommandRelease - the start,stop in the window definition are relative to the command release.

OnSuccess
    Defines what happens when the verification returns true. It has to be one of:

    * SUCCESS: command considered completed successful (CommandComplete event is generated)
    * FAIL:  CommandFailed event is generated
    * none (default) – only a Verification_stage event is generated without an effect on the final execution status of the command.

OnFail
    Same like OnSuccess but the evnet is generated in case the verifier returns false.

OnTimeout
    Same as OnSuccess but the event is generated in case the verifier times out.


Change Log Sheet
----------------

This sheet must be named *ChangeLog*, and the columns described must not be reordered.
This sheet contains the list of the revision made to the MDB.
