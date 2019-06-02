Commands Sheet
==============

This sheet contains commands description, including arguments. General convention:

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
