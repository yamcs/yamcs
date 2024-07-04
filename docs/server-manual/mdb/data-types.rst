Data Types
==========

The MDB data types are associated to parameters and command arguments and provide several characteristics of these:

- the value type (int64, int32, float,...) of the engineering value
- the value type of the raw value
- validity conditions
- units
- alarms (only for types corresponding to parameters)
- engineering/raw transformation using calibrators
- raw/binary transformation using data encodings

The distinction between a parameter and its type is not so evident and many control systems do not make this distinction (i.e. each parameter with its own type). 

In practice most use of shared types has been to define generic types such as ``uint8``, ``uint16``, and use those for parameters that do not require any calibration, units or other specific properties.

Types can also be shared for parameters associated to the same type of sensors which do not need individual calibrators.


Yamcs supports the following parameter and argument data types:

* Integer data type
* Float data type
* Boolean data type
* String data type
* Binary data type
* Absolute time data type
* Enumerated data type - a (integer, string) pair.
* Aggregate data type - complex data type similar to a C-struct. Each member of the aggregate has a name and a type. 
* Array data type - multidimensional array where each element is of the same type.

As mentioned above, one important function of a data type is to describe how to represent the raw value on the wire (i.e. in the command or telemetry packet). The following encodings are supported:

- Integer data encoding 
- Float data encoding
- Boolean data encoding
- String data encoding
- Binary data encoding

Note that ``xyz`` in the ``xyz data encoding`` refers to the type of the raw value whereas the ``xyz`` in ``xyz data type`` refers to type of the engineering value.

One will certainly notice that there is no direct encoding for absolute times, enumerated, aggregated and array value types. Currently these can only be encoded/decoded by other means (e.g. an aggregate value will be decoded by decoding its members, an enumerated value by decoding its integer or string representation).

The integer and float encodings have optionally a calibrator which allow transforming the raw value to engineering value or reverse.

There may be MDB data types without encoding - these are used by local parameters which are never encoded on wire.

All the data encodings in Yamcs can be performed by user defined java code by implementing the :javadoc:`org.yamcs.mdb.DataEncoder` or :javadoc:`org.yamcs.mdb.DataDecoder` respectively. Such code has to be written if the encoding format is not part of Yamcs.


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
        <ValidRange minInclusive="100" maxInclusive="1000"/>
    </IntegerParameterType>


Note that by default the type has a ``sizeInBits=32`` so the value will be converted from 16 bits on the wire to 32 bits value.
Yamcs will use a 32 bit integer for any parameter with ``sizeInBits <= 32`` and a 64 bit integer for any type with the ``32 < sizeInBits <= 64``.

The ``<ValidRange>`` construct is optional and used differently for parameters and arguments:

* for parameters it is used to check the validity. If a parameter value does not satisfy the range, it will be marked as invalid (and can be seen with a specific color in the display)
* for arguments it is used to verify the value provided by the user. If the value does not match the range, the command is rejected.

Integer parameters can also have associated alarms and calibrators (see below an example for float parameters, it is identical for integer parameters).
 
One important thing to mention about calibrators is that even when associated to the integers, they still work on (signed) double floating point numbers. Some precision will be lost when converting from a large (unsigned) integer to a double or vice versa.
 

The integer parameters can also be encoded as strings, as in the following XTCE example:

.. code-block:: xml
  
   <IntegerParameterType signed="false" name="int_encoded_as_string">
        <StringDataEncoding>
            <SizeInBits>
                <Fixed>
                    <FixedValue>48</FixedValue>
                </Fixed>
                <TerminationChar>00</TerminationChar>
            </SizeInBits>
        </StringDataEncoding>
   </IntegerParameterType>
  
In this case the raw value will be of type string and the engineering value of type integer. For an explanation of how the string encoding works, please see below in the String data type section.

  
Float data type
----------------

Floating point data in Yamcs can be simple precision (32 bit) or double precision (64 bit).

It can be encoded/decoded either to a IEEE754 representation or to an integer representation using a calibration function. Typically a sensor will produce a digital value (e.g. 12 bits integer) which has to be converted to an analog value using a calibration (or transfer) function. 

An XTCE example of a float parameter encoded as integer and having a polynomial calibrator:

.. code-block:: xml

    <FloatParameterType initialValue="20" name="Temperature_Type">
        <UnitSet>
            <Unit>degC</Unit>
        </UnitSet>
        <IntegerDataEncoding encoding="unsigned" sizeInBits="12">
            <DefaultCalibrator>
                <PolynomialCalibrator>
                    <Term coefficient="0" exponent="-20" />
                    <Term coefficient="1" exponent=".025" />
                </PolynomialCalibrator>
            </DefaultCalibrator>
        </IntegerDataEncoding>
        <DefaultAlarm>
            <StaticAlarmRanges>
                <WarningRange minInclusive="10" maxInclusive="30" />
                <CriticalRange minInclusive="-10" maxInclusive="50" />
                </StaticAlarmRanges>
        </DefaultAlarm>
    </FloatParameterType>

Yamcs supports the following type of calibrations:

- polynomial - the conversion between the raw value and the engineering value is obtained by applying a polynomial function.
- linear spline (point pairs) - the conversion between the raw and engineering value is obtained by interpolating linearly the raw value.
- mathematical operations specified in reverse polish notation (only in XTCE format) - the conversion is obtained by applying the mathematical operation.
- Java expressions (only in spreadsheet format) - the conversion is obtained by running it through the java expression.
 
The java expression is the most flexible calibration as it can practically call any java code available on the server. However it is not allowed by XTCE (instead an algorithm can be used to generate the output value into a different parameter).

The example above also defines an default alarm - perhaps a bit counter intuitive the parameter will trigger the alarm if it is outside of the range defined there (for example a value of 40 will trigger the warning alarm and a value of -15 will trigger the critical alarm).  As per XTCE there are 5 levels of alarms supported (in order of severity): watch, warning, distress, critical and severe.

Both calibrators and alarms can be contextualized: that means a different alarm or calibrator will be used depending on the value of other parameters.

While the most common encoding for float is float encoding, the other encodings can also be used:

- integer: will convert number to integer by performing a java cast to long and then fitting the long into the number of bits required. This may result in loss of precision and even in completely wrong number when converting a signed float to a unsigned integer. 
- string: the value will be converted to a string representation.
- binary: 


Boolean data type
-----------------

Boolean values in Yamcs take take a simple ``true`` or ``false`` value. In XTCE one can define different values instead of ``true``/``false`` as in the example below. Yamcs only supports these values when reading the XTCE file (they can be used in conditions for example) but the value computed does not include the string (and thus cannot be shown in the display).

To encode boolean values one can use any data encoding with the following transformations:

- for integer/float raw values: 

  - decoding: ``0`` is ``false`` and anything else is true when decoding. 
  - encoding: ``true`` is converted to ``1``, ``false`` is converted to ``0``.
- for string values: 

  - decoding: if the string value is empty, case insensitive equal with the ``zeroStringValue`` defined in the type or with the string ``0`` then the value is ``false``, anything else is ``true``. 
  - encoding: ``true`` is converted to the ``oneStringValue`` defined in the type, ``false`` is converted to ``zeroStringValue`` defined in the type.
- for binary values: 

  - decoding: if the binary value is empty or consists only of nulls then the value of the boolean is ``false`` anything else is ``true``.
  - encoding: the value is converted to a binary array of one element with the value ``1`` if ``true`` or ``0`` if ``false``.

.. code-block:: xml

    <BooleanParameterType name="bool2" oneStringValue="yes!" zeroStringValue="nooo">
        <StringDataEncoding>
            <SizeInBits>
                <Fixed>
                    <FixedValue>32</FixedValue>
                </Fixed>
                <TerminationChar>00</TerminationChar>
            </SizeInBits>
        </StringDataEncoding>
    </BooleanParameterType>
        
The spreadsheet format allows to define a data type with  a boolean data encoding by using a raw type of ``bool`` in the Data Type definition. This encoding is not possible to be defined in XTCE (but it is equivalent with a 1 bit integer encoding) and it always uses one bit representation with ``0 = false`` and ``1 = true``.  


String data type
----------------

In Yamcs the string data is represented as a java (unicode) String value. The encoding to/from the wire is performed using a string data encoding with one of the supported `Java Charsets <https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html>`_ (UTF-8, ISO-8859-1, etc)

In addition to converting the bytes to unicode characters, a typical problem in decoding telemetry is knowing the boundary of the string inside the packet. To comply with XTCE, Yamcs implements a "string in a buffer" approach:

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


.. rubric:: Example 1: string encoded in a fixed size buffer with a null terminator

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


.. rubric:: Example 2: prefixed size string encoded in undefined buffer

The buffer is not explicitly defined so it is effectively as long as the prefix + string.
The ``maxSizeInBits`` refers to the size of the buffer, so in this example the maximum size of the string will be 4.

Note the ``_yamcs_ignore`` parameter reference which is used to workaround XTCE mandating a dynamic value. Yamcs will accept the XML file without the ``DynamicValue`` section but the file will not validate with XTCE 1.2 xsd. An alternative for the ``_yamcs_ignore`` would be to derive the buffer length from the packet length.

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

.. rubric:: Example 3: null terminated string encoded in undefined buffer

This examples provides string argument type whose size is variable. The buffer is not defined which means the buffer will be effectively the string + terminator.

The maxSizeInBits refers to the maximum size of the buffer; it means that the maximum size of the string in binary is ``maxSizeInBits/8 - 1``.

Note the _``yamcs_ignore`` parameter reference which is used to workaround XTCE mandating a dynamic value. Yamcs will accept the XML file without the ``DynamicValue`` section but the file will not validate with XTCE 1.2 xsd. An alternative for the ``_yamcs_ignore`` would be to define an argument for the buffer length but that would be inconvenient for the user.

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

More XTCE examples:

* :source:`yamcs-core/src/test/resources/xtce/strings-tm.xml`
* :source:`yamcs-core/src/test/resources/xtce/strings-cmd.xml`

More Spreadsheet examples:

* :source:`yamcs-core/mdb/refmdb.xls`

Finally, we mention that string values can also be encoded with a binary encoder; the translation from string to binary is using the `String#getBytes <https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#getBytes>`_ method.


Binary data type
----------------

A binary data type represents a sequence of bytes (a byte[] in java). The values of this type implicitly have a length.

As for strings, Yamcs only supports types which are an integer number of bytes.

Unlike strings, when encoding binary values there is no distinction between the value being encoded and the buffer in which the value is encoded: the value always fills the buffer.


.. rubric:: Example 1: binary parameter type of fixed size

.. code-block:: xml

    <BinaryParameterType name="binary_type1">	
        <BinaryDataEncoding>
            <SizeInBits>
                <FixedValue>128</FixedValue>
            </SizeInBits>
        </BinaryDataEncoding>
    </BinaryParameterType>

A parameter of this type will always be 16 bytes in length. 


.. rubric:: Example 2: binary parameter type of variable size with the size given by another parameter

The example below defines a parameter type whose size is given by another parameter named ``size``. That parameter has to be of integer type and precede the binary one in the packet.

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


.. rubric:: Example 3: binary argument type of variable size with the size encoded in front of the data

The example above needs another parameter for the data size. When used in command it has the disadvantage that the user needs to enter the number of bytes in addition to the bytes themselves (with the risk of introducing inconsistencies). Yamcs allows to use an algorithm which will perform the encoding without the addition of the extra argument:


.. code-block:: xml

    <BinaryArgumentType name="barray">
        <AncillaryDataSet>
            <AncillaryData name="Yamcs">minLength=2</AncillaryData>
            <AncillaryData name="Yamcs">maxLength=10</AncillaryData>
        </AncillaryDataSet>
        <BinaryDataEncoding>       
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
Instead of encoding and decoding time using raw integer or binary parameters, Yamcs supports the AbsoluteTimeParameterType to describe time. This parameter can be encoded using on of ``BinaryDataEncoding``, ``FloatDataEncoding``, ``IntegerDataEncoding`` and ``StringDataEncoding`` elements. 

The following example displays the use of a ``IntegerDataEncoding`` element where ``scale`` and ``offset`` attributes are used to apply a linear transformation to the incoming value in order to parse the proper time value. 

.. rubric:: Example 1: integer encoding for a AbsoluteTimeParameterType parameter

The example below is using UNIX as its reference time, whose count starts at January 1 1970 and is used by modern computers, linux systems etc. The offset and the scale are part of a linear transformation which has the form ``y = ax + b`` where ``b`` represents the offset, ``a`` represents the scale and ``x`` is the input.

This transformation could be used for a system whose internal clock counts in seconds from 1/1/2000, so we need to add ``946677600`` seconds to that time in order to get the appropriate UNIX timestamp. 

- ``<ReferenceTime>`` describes origin(epoch or reference) of this time type
- ``<Epoch>`` may be specified as an XS date where time is implied to be 00:00:00, xs dateTime, or string enumeration of common epochs. The enumerations are TAI(used by CCSDS and others), J2000, UNIX(also known as POSIX) and GPS

.. code-block:: xml

    <AbsoluteTimeParameterType name="absolute_time_param_type_example">
        <Encoding offset="946677600" scale="1">
            <IntegerDataEncoding sizeInBits="32" />
        </Encoding>
        <ReferenceTime>
            <Epoch>UNIX</Epoch>
        </ReferenceTime>
    </AbsoluteTimeParameterType>


Enumerated data type
--------------------

The EnumeratedParameterType supports the description of enumerations, which are a list of values and their associated labels. Below is an example that demonstrates how an enumerated parameter type is declared and its mostly used attributes:

.. rubric:: Example 1: simple enumerated parameter declaration

.. code-block:: xml

    <EnumeratedParameterType name="enumerated_parameter_type_example">
        <IntegerDataEncoding sizeInBits="16"/>
            <EnumerationList>
                <Enumeration value="0" label="label_1" />
                <Enumeration value="2" label="label_2" />
                <Enumeration value="4" label="label_3" />
                <Enumeration value="6" label="label_4" />
            </EnumerationList>
    </EnumeratedParameterType>


Aggregate data type
-------------------

The AggregateParameterType is used to describe aggregates. It is similar to C-structs or records
in other languages. The ArrayParameterType is defined as shown in the example below:

.. rubric:: Example 1: simple aggregate parameter declaration

``<Member>`` is used to define members of the aggregate. Each member has a ``name``, a ``typeRef`` for its type and an optional ``initialValue`` for a possible predefined value.

.. code-block:: xml

    <AggregateParameterType name="aggregate_parameter_type_example"  shortDescription="Aggregate Parameter Type Example">
        <MemberList>
            <Member name="member_1" typeRef="bool_t"/>
            <Member name="member_1" typeRef="uint16_t" initialValue="5"/>
            <Member name="member_1" typeRef="float_t"/>
        </MemberList>
    </AggregateParameterType>


Array data type
---------------


The ArrayParameterType is used to describe arrays of other ParameterTypes. It is used in containers that are formed dynamically. 
This happens when the number of the container's parameters depends on a specific parameter's value. In that part of the container that will be dynamically repeated an ``ArrayParameterRefEntry`` is injected.
The ArrayParameterType is defined as shown in the example below:

- ``arrayTypeRef`` is a reference to another ParameterType from which the array cells are formed. Any parameter type can be used.
- ``DimensionList`` describes the dimensions of the array. Can be static or dynamic (value from another parameter).


.. rubric:: Example 1: simple array parameter declaration with predefined size = 6

.. code-block:: xml

    <ArrayParameterType name="array_parameter_type_example" arrayTypeRef="other_parameter_type">
        <DimensionList>
            <Dimension>
                <StartingIndex>
                    <FixedValue>0</FixedValue>
                </StartingIndex>
                <EndingIndex>
                    <FixedValue>5</FixedValue>
                </EndingIndex>
            </Dimension>
        </DimensionList>
    </ArrayParameterType>

.. rubric:: Example 2: simple array parameter declaration with dynamic size

In this example, the size of the array is equal to the integer parameter ``number_of_parameters``. The ``<LinearAdjustment>`` element is used because the final array size will be equal to ``<EndingIndex> - <StartingIndex> + 1``   

.. code-block:: xml

    <ArrayParameterType name="array_parameter_type_example" arrayTypeRef="other_parameter_type">
        <DimensionList>
            <Dimension>
                <StartingIndex>
                    <FixedValue>0</FixedValue>
                </StartingIndex>
                <EndingIndex>
                    <DynamicValue>
                        <ParameterInstanceRef parameterRef="number_of_parameters" />
                        <LinearAdjustment intercept="-1" />
                    </DynamicValue>
                </EndingIndex>
            </Dimension>
        </DimensionList>
    </ArrayParameterType>
