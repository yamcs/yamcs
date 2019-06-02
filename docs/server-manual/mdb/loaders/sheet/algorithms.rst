Algorithms Sheet
================

This sheet contains arbitrarily complex user algorithms that can set (derived) output parameters based on any number of input parameters.

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

These algorithms have to implement the interface ``org.yamcs.xtceproc.DataDecoder``.
