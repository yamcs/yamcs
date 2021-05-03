Data Types
=====================

The MDB data types are associated to parameters and command arguments and provide several characteristics of these:

- the value type (int64, int32, float,...) of the engineering value
- the value type of the raw value
- validity conditions
- units
- alarms (only for types corresponding to parameters)
- engineering/raw transformation
- raw/binary transformation

Yamcs supports the following parameter and argument data types:

- Integer data type
- Float data type
- Boolean data type
- String data type
- Binary data type
- Absolute time data type
- Enumerated data type - a (integer, string) pair.
- Aggregate data type - complex data type similar to a C-struct. Each member of the aggregate has a name and a type. 
- Array data type- multidimensional array where each element is of the same type.


To encode/decode data to/from binary (telemetry/command packet), the following encodings are supported:

- Integer data encoding 
- Float data encoding
- Boolean data encoding
- String data encoding
- Binary data encoding

Note that the ``xyz`` in the ``xyz data encoding`` refers to the type of the raw value whereas the ``abc`` in the ``abc data type`` refers to type of the engineering value.

One will quickly notice that there is no direct encoding for absolute times, enumerated, aggregated and array value types. Currently these can only be decoded/encoded from/to binary by other means (e.g. an aggregate value will be decoded by decoding its members, an enumerated value by decoding its integer or string representation).

The integer and float encodings have optionally a calibrator which allow transforming the raw value to engineering value or reverse.

There may be MDB data types without encoding - these are used by local parameters which are never encoded on wire.

All the data encodings in Yamcs can be performed by user defined java code by implementing the :javadoc:`org.yamcs.xtceproc.DataEncoder` or :javadoc:`org.yamcs.xtceprocDataDecoder` respectively. Such code has to be written if the encoding format is not part of Yamcs.

Parameter types vs Argument types
---------------------------------

The data types described in this section are used both for parameters and command arguments. Internally in Yamcs the types are not shared.

For convenience, when defining the Mission Database in spreadsheet format, there is one place where all the data types are defined. However when Yamcs loads the spreadsheet, it duplicates in memory the definition for the parameters and arguments.

In XTCE they are defined in different sections: ``<ParameterTypeSet>`` and ``<ArgumentTypeSet>``.

Note that the calibrator (if defined) applies in a different direction: for parameter types it converts from raw to engineering value whereas for argument types it converts from engineering value to raw. Thus one cannot apply the same calibrator even if a parameter  corresponds conceptually to an argument. The user would have to invert (in mathematical terms) the calibrator used in the parameter type definition when defining the corresponding argument data type.



Integer data type
-----------------

Integer values in Yamcs can be 32 or 64 bits signed or unsigned.

Integer values can be encoded/decoded on any number of bits smaller than 64. Signed and unsigned values are supported. Signed values can be encoded in twos complement, sign magnitude or ones complement.

A simple XTCE example of an unsigned integer parameter type with an integer encoding:

.. code-block:: xml

    <IntegerParameterType signed="false" name="uint16">
        <IntegerDataEncoding encoding="unsigned" sizeInBits="16" />
    </IntegerParameterType>


Note that by default the type has a ``sizeInBits=32`` so the value will be converted from 16 bits on the wire to 32 bits value.
Yamcs will use a 32 bit integer for any parameter with ``sizeInBits <= 32`` and a 64 bit integer for any type with the ``32 < sizeInBits <= 64``.

The following example shows a more exotic encoding of an integer parameter as a string:

.. code-block:: xml
  
   <xtce:IntegerParameterType signed="false" name="int_encoded_as_string">
        <xtce:StringDataEncoding>
            <xtce:SizeInBits>
                <xtce:Fixed>
                    <xtce:FixedValue>48</xtce:FixedValue>
                </xtce:Fixed>
                <xtce:TerminationChar>00</xtce:TerminationChar>
            </xtce:SizeInBits>
        </xtce:StringDataEncoding>
   </xtce:IntegerParameterType>
  
In this case the raw value will be of type string and the engineering value of type integer. For an explanation of how the string encoding works, please see below in the String data type section.

  
Float data type
----------------

Floating point data in Yamcs can be simple precision (32  bit) or double precision (64 bit).

It can be encoded/decoded either to a IEEE754 representation or to an integer representation using a calibration function. Typically a sensor will produce a digital value (e.g. 12 bits integer) which has to be converted to an analog value using a calibration (or transfer) function. 

An XTCE example of a float parameter encoded as integer and having a polynomial calibrator to convert from the raw integer value to the float engineering value:

.. code-block:: xml

    <FloatParameterType initialValue="-0.6" name="Power_Level_Type">
        <UnitSet>
            <Unit>dB</Unit>
        </UnitSet>
        <IntegerDataEncoding encoding="twosComplement" sizeInBits="16">
            <DefaultCalibrator name="Default_Counts">
                <PolynomialCalibrator>
                    <Term coefficient="1.5" exponent="0" />
                    <Term coefficient="1" exponent="1" />
                </PolynomialCalibrator>
            </DefaultCalibrator>
        </IntegerDataEncoding>
    </FloatParameterType>


Boolean data type
-----------------
TBW

String data type
----------------

In Yamcs the string data is represented as a java (unicode) String value. The encoding to/from the wire is performed using one of the supported java charsets <https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html>`_ (UTF-8, ISO-8859-1, etc)

In addition to converting the bytes to unicode character, a typical problem in decoding telemetry is to know the boundary of the string inside the packet. To comply with XTCE Yamcs implements a "string in a buffer" approach:

- conceptually the packet contains a buffer (or a box) where the string has to be extracted from or encoded into.
- the buffer can be the same size with the string or larger than the string. If the buffer is larger than the string, it will be filled by Yamcs with 0 for commands or some filler which is ignored by Yamcs for telemetry.
- if the buffer is larger than the string, the buffer size can be fixed or its size can be determined from the value of a parameter/argument.
- inside the buffer: 
  - the string can fill completely the buffer (so the size of the string is determined by the size of the buffer).
  - the size of the string can be encoded at the beginning of the buffer (in front of the string)
  - or the string can be terminated by a special character (or by the end of the buffer, whichever comes first).
  
One case which is not supported by Yamcs (nor by XTCE) is a fixed size string inside a fixed size buffer with the string not filling completely the buffer. For this case you can limit the size of the buffer to the size of the string and define another parameter for the remaining of the buffer, or simply define an offset for the next container entry.

The size of the buffer is in number of bytes - depending on the encoding used, a character of the string may be encoded on multiple bytes (for example UTF-8 encodes each character in one to four bytes).

Finally, please note that although XTCE defines a number of bits for the buffer size or for the size tag, Yamcs only supports encoding these on an integer number of bytes (e.g. encoding strings on partial bytes is not supported) so the number of bits has to be divisible by 8.


**Example 1: string encoded in a fixed size buffer with a null terminator**

The buffer is 6 bytes long (meaning that the next parameter will come after the 6 bytes even if the string is shorter). 
If the terminator is not found, it is not considered an error and the string will be 6 bytes long.
If the terminator is not specified (by removing the ``<TerminationChar>`` section), the string will always be 6 bytes long.
Note that it may cause the string to include nulls but that is not a problem in Java.

.. code-block:: xml

    <StringParameterType name="string1">
        <StringDataEncoding encoding="UTF-8">
            <SizeInBits>
                <Fixed>
                    <FixedValue>48</FixedValue>
                </Fixed>
                <TerminationChar>00</TerminationChar>
            </SizeInBits>
        </StringDataEncoding>
    </StringParameterType>

This example can be defined in the spreadsheet with the encoding ``terminated(0x00, UTF-8, 48)``. If there is no terminator (so the string covers all the time the buffer), the equivalent spreadsheet encoding is ``fixed(48, UTF-8)``.


**Example 2:  prefixed size string encoded in undefined buffer**
The buffer is not explicitely defined so it is effectively as long as the prefix + string.
The maxSizeInBits refers to the size of the buffer, so in this example the maximum size of the string will be 4.

Note the _yamcs_ignore parameter reference which is used to workaround XTCE mandating a dynamic value. Yamcs will accept the XML file without the ``DynamicValue`` section but the file will not validate with XTCE 1.2 xsd. An alternative for the ``_yamcs_ignore`` would be to derive the buffer length from the packet length.

.. code-block:: xml

    <StringParameterType name="string5">
        <StringDataEncoding encoding="UTF-8">
            <Variable maxSizeInBits="48">
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="_yamcs_ignore" />
                </DynamicValue>
                <LeadingSize sizeInBitsOfSizeTag="16" />
            </Variable>
        </StringDataEncoding>
    </StringParameterType>

This example can be best defined in the spreadsheet with the encoding ``PrependedSize(16)``. The maximum size cannot be defined, so the effective maximum size will be the remaining of the packet.

**Example 3: null terminated string encoded in undefined buffer**
This examples provdides string argument type whose size is variable. The buffer is not defined which means the buffer will be effectively the string + terminator.

The maxSizeInBits refers to the maximum size of the buffer; it means that the maximum size of the string in binary is ``maxSizeInBits/8 - 1``.

Note the _yamcs_ignore parameter reference which is used to workaround XTCE mandating a dynamic value. Yamcs will accept the XML file without the ``DynamicValue`` section but the file will not validate with XTCE 1.2 xsd. An alternative for the ``_yamcs_ignore`` would be to define an argument for the buffer length but that would be inconvenient for the user.

.. code-block:: xml

    <StringArgumentType name="string3">
        <StringDataEncoding encoding="UTF-8">
            <Variable maxSizeInBits="48">
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="_yamcs_ignore" />
                </DynamicValue>
                <TerminationChar>00</TerminationChar>
            </Variable>
        </StringDataEncoding>
    </StringArgumentType>

More XTCE examples can be found in `<https://github.com/yamcs/yamcs/blob/master/yamcs-core/src/test/resources/xtce/strings-tm.xml>`_ and `<https://github.com/yamcs/yamcs/blob/master/yamcs-core/src/test/resources/xtce/strings-cmd.xml>`_

More Spreadsheet examples can be found in `<https://github.com/yamcs/yamcs/blob/master/yamcs-core/mdb/refmdb.xls>`_


Binary data type
----------------

A binary data type represents a sequence of bytes (a byte[] in java). The values of this type implicitly have a length.

As for strings, Yamcs only supports types which are an integer number of bytes.

Unlike strings, when encoding binary values there is no distinction between the value being encoded and the buffer in which the value is encoded: the value always fills the buffer.

**Example 1: binary parameter type of fixed size**

.. code-block:: xml

    <BinaryParameterType name="binary_type1">	
        <BinaryDataEncoding>
            <SizeInBits>
                <FixedValue>128</FixedValue>
            </SizeInBits>
        </BinaryDataEncoding>
    </BinaryParameterType>

A parameter of this type will always be 16 bytes in length. 
    
**Example 2: binary parameter type of variable size with the size given by another parameter**

The example below defines a parameter type whose size is given by another parameter named ``size``. That parameter has to be of integer type and preceede the binary one in the packet.

.. code-block:: xml

    <BinaryParameterType name="BinaryType">
        <BinaryDataEncoding>
            <SizeInBits>
                <DynamicValue>
                    <ParameterInstanceRef parameterRef="size" />
                    <LinearAdjustment slope="8" />
                </DynamicValue>
            </SizeInBits>
    </BinaryDataEncoding>
    
Note the ``<LinearAdjustment>`` construct which allows to convert from number of bytes to number of bits required by the ``<SizeInBits>`` element.


**Example 3: binary argument type of variable size with the size encoded in front of the data**

The example above needs another parameter for the data size. When used in command it has the disatvantage that the user needs to enter the number of bytes in addition to the bytes themselves (with the risk of introducing inconsistencies). Yamcs allows to use an algorithm which will perform the encoding without the addition of the extra argument:


.. code-block:: xml

    <xtce:BinaryArgumentType name="barray">
        <xtce:AncillaryDataSet>
            <xtce:AncillaryData name="Yamcs">minLength=2</xtce:AncillaryData>
            <xtce:AncillaryData name="Yamcs">maxLength=10</xtce:AncillaryData>
        </xtce:AncillaryDataSet>
        <xtce:BinaryDataEncoding>       
            <SizeInBits> 
                 <DynamicValue>
                    <ParameterInstanceRef parameterRef="_yamcs_ignore" />
                </DynamicValue>
            </SizeInBits>
            <ToBinaryTransformAlgorithm name="LeadingSizeBinaryEncoder">
                <!-- the 16 passed to the constructor means the size is encoded on 16 bits -->
                <AlgorithmText language="java">
                    org.yamcs.algo.LeadingSizeBinaryEncoder(16)
                </AlgorithmText>
            </ToBinaryTransformAlgorithm>
        </BinaryDataEncoding>
    </BinaryArgumentType>

Note again the ``<DynamicValue>`` construct with a reference to ``_yamcs_ignore`` which will make yamcs ignore this section. The ``<SizeInBits>`` section can be removed from the file if XSD compliance is not important, Yamcs will not complain.

Note also the minLength and maxLength which are used to configure the minimum/maximum length of the accepted data (not including the 16 bits size tag!).

    
Absolute time data type
-----------------------
TBW

Enumerated data type
--------------------
TBW

Aggregate data type
-------------------
TBW

Array data type
---------------
TBW
