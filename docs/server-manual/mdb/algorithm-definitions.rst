Algorithm Definitions
=====================

Algorithms are user scripts that can perform arbitrary logic on a set of incoming parameters. The result is typically one or more derived parameters, called *output parameters*, that are delivered together with the original set of parameters (at least, if they have been subscribed to).

Output parameters are very much identical to regular parameters. They can be calibrated (in which case the algorithm's direct outcome is considered the raw value), and they can also be subject to alarm generation.

Algorithms can be written in JavaScript, Python or Java. By default Yamcs supports JavaScript algorithms executed using the Nashorn JavaScript engine. Support for other languages (e.g. Python) requires installing additional dependencies.


Yamcs will bind these input parameters in the script's execution context, so that they can be accessed from within there. In particular the following attributes and methods are made available:

value
    the engineering value
rawValue
    the raw value (if the parameter has a raw value)
monitoringResult
    the result of the monitoring: *null*, ``DISABLED``, ``WATCH``, ``WARNING``, ``DISTRESS``, ``CRITICAL`` or ``SEVERE``.
rangeCondition
    If set, one of ``LOW`` or ``HIGH``.
generationTimeMillis
    The parameter generation time - milliseconds since Yamcs epoch.
aquisitionTimeMillis
    The parameter acquisition time - milliseconds since Yamcs epoch.
generationTime()
    The parameter generation time converted to Java Instant (by removing the leap seconds).
aquisitionTime()
    The parameter acquisition time converted to Java Instant (by removing the leap seconds).
    
    
If there was no update for a certain parameter, yet the algorithm is still being executed, the previous value of that parameter will be retained.


Triggers
--------

Algorithms can trigger on two conditions:

#. Whenever a specified parameter is updated
#. Periodically

Multiple triggers can be combined. In the typical example, an algorithm will trigger on updates for each of its input parameters. In other cases (for example because the algorithm doesn't have any inputs), it may be necessary to trigger on some other parameter. Or maybe a piece of logic just needs to be run at regular time intervals, rather than with each parameter update.

If an algorithm was triggered and not all of its input parameters were set, these parameters *will* be defined in the algorithm's scope, but with their value set to ``null``.


User Libraries
--------------

The Yamcs algorithm engine can be configured to import a number of user libraries. Just like with algorithms, these libraries can contain any sort of logic and are written in the same scripting language. Yamcs will load user libraries *one time only* at start-up in their defined order. This will happen before running any algorithm. Anything that was defined in the user library, will be accessible by any algorithm. In other words, user libraries define a kind-of global scope. Common use cases for libraries are: sharing functions between algorithms, shortening user algorithms, easier outside testing of algorithm logic, ...

Allowing to split the code in different user libraries is merely a user convenience. From the server perspective they could all be merged together in one big file.


Algorithm Scope
---------------

User algorithms have each their own scope. This scope is safe with respect to other algorithms (i.e. variables defined in algorithm *a* will not leak to algorithm *b*.

An algorithm's scope, however, is shared across multiple algorithm runs. This allows you to keep variables inside internal memory if needed. Do take caution with initializing your variables correctly at the beginning of your algorithm if you only update them under a certain set of conditions (unless of course you intend them to keep their value across runs).


Sharing State
-------------

If some kind of a shared state is required between multiple algorithms, the user libraries' shared scope could be used for this. In many cases, the better solution would be to just output a parameter from one algorithm, and input it into another. Yamcs will automatically detect such dependencies, and will execute algorithms in the correct order.


Historic Values
---------------

With what has been described so far, it would already be possible to store values in an algorithm's scope and perform windowing operations, such as averages. Yamcs goes a step further by allowing you to input a particular *instance* of a parameter. By default instance *0* is inputted, which means the parameter's actual value. But you could also define instance *-1* for inputting the parameter's value as it was on the previous parameter update. If you define input parameters for, say, each of the instances *-4*, *-3*, *-2*, *-1* and *0*, your user algorithm could be just a simple one-liner, since Yamcs is taking care of the administration.

Algorithms with windowed parameters will only trigger as soon as each of these parameters have all instances defined (i.e. when the windows are full).


JavaScript algorithms
---------------------

The JavaScript algorithms are executed by the Nashorn engine.

The algorithm text is expected to contain the full function body. The body will be encapsulated in a JavaScript function like:

.. code-block:: javascript

    function algorithm_name(in_1, in_2, ..., out_1, out_2...) {
        <algorithm-text>
    }


``in_x`` and  ``out_x`` are names assigned to the inputs/outputs in the algorithm definition.

The method can make use of the input variables and assign ``out_x.value`` (this is the engineering value) or ``out_x.rawValue`` (this is the raw value) and ``out_x.updated`` for each output variable.

The ``<out>.updated`` can be set to false to indicate that the output value has not to be further processed even if the algorithm has run. By default it is true, meaning that each time the algorithm is run, it is assumed that it updates all the output variables.

If ``out_x.rawValue`` is set and ``out_x.value`` is not, then Yamcs will run a calibration to compute the engineering value.

Note that some algorithms (e.g. command verifiers) need to return a value.


Python algorithms
-----------------

This works very similarly with the JavaScript algorithms. The thing to pay attention is the indentation. The algorithm text which is specified in the spreadsheet will be automatically indented with 4 characters:

.. code-block:: python

    function algorithm_name(in_1, in_2, ..., out_1, out_2...) {
        <algorithm-text>
    }


Java expression algorithms
--------------------------

This works similarly with the JavaScript and Python algorithms: a java class is generated containing the user defined algorithm text. It offers better performance than the scripting algorithms because no script engine is involved.

.. code-block:: java

    ... imports
    ... class declaration
    private void execute_java_expr(ParameterValue input0, ParameterValue input1..., ParameterValue output0, ParameterValue output1...) {
        <algorithm-text>
    }

The first variables are the inputs, followed by the outputs.
The java class :javadoc:`org.yamcs.parameter.ParameterValue` has to be used to get the values of the inputs (e.g. ``getEngValue()`` will give the engineering value) and set the value of the outputs. For example the text to add two inputs ``pv0`` and ``pv1`` into ``AlgoFloatAdditionJe`` could be:

.. code-block:: java

    float f0 = pv0.getEngValue().getFloatValue();
    float f1 = pv1.getEngValue().getFloatValue();
    AlgoFloatAdditionJe.setFloatValue(f0 + f1);

The ``getFloatValue()`` in the code above is because the engineering type is Float with sizeInBits=32. If the wrong get is used on a  :javadoc:`org.yamcs.parameter.Value`, an exception will be thrown by the algorithm (should be visible in the yamcs-web as well as in the logs).

The algorithm can leave the output values unset; in that case the values will not be used further.

In case the algorithm is used for a command verifier (see below), it has to return a value. A boolean value of ``true`` (in fact java ``Boolean.TRUE`` object) means that the verifier has succeeded, ``null`` means that the verifier is still pending. Any other value means that the verifier has failed; the object will be converted to string and used as an explanation for the failure.

    
Java algorithms
---------------

The algorithm text is a class name with optionally parentheses enclosed string that is parsed into an object by a yaml parser. Unlike the java-expression algorithms, the Java algorithms require the user to pre-compile the classes into a jar and place it on the server in the lib/ext directory.

Yamcs will locate the given class which must be implementing the :javadoc:`org.yamcs.algorithms.AlgorithmExecutor` interface and will create an object with a constructor with three parameters:

.. code-block:: java

    MyAlgorithmExecutor(Algorithm algorithmDef, AlgorithmExecutionContext context, Object arg)

* ``algorithmDef`` represents the algorithm definition; it can be used for example to retrieve the MDB algorithm name, input parameters, etc.
* ``context`` is an object holding some contextual information related to where the algorithm is running. Generally this refers to a processor but for command verifiers there is a restricted context to distinguish the same algorithm running as verifier for different commands.
* ``arg`` is an optional argument parsed using the snakeyaml parser (can be a Integer, Long, Double, Map or List).

If the optional argument is not present in the algorithm text definition,  then the class constructor  should only have two parameters.

The class has two main methods ``updateParameters`` which is called each time one of input parameters changes and ``runAlgorithm`` which runs the algorithm and returns the output values. The algorithm is free to chose which output values are returned at each run (it could also return an empty list when no value has been generated).

The abstract class :javadoc:`org.yamcs.algorithms.AbstractAlgorithmExecutor` offers some helper methods and can be used as base class for implementation of such algorithm.

If the algorithm is used for data decoding, it has to implement the :javadoc:`org.yamcs.mdb.DataDecoder` interface instead (see below).


Command verifier algorithms
---------------------------

Command verifier algorithms are special algorithms associated to the command verifiers. Multiple instances of the same algorithm may execute in parallel if there are multiple pending commands executed in parallel.

These algorithms are special as they can use as input variables not only parameters but also command arguments and command history events. These are specified by using "/yamcs/cmd/arg/" and "/yamcs/cmdHist" prefix respectively.

In addition these algorithms have to return a boolean value (whereas the normal algorithms only have to write to output variables). The returned value is used to indicate if the verifier has succeeded or failed. No return value will mean that the verifier is still pending.


Data Decoding algorithms
------------------------

The Data Decoding algorithms are used to extract a raw value from a binary buffer. These algorithms do not produce any output and are triggered whenever the parameter has to be extracted from a container.

These algorithms work differently from the other ones and have are some limitations:

* only Java is supported as a language
* not possible to specify input parameters

These algorithms have to implement the interface :javadoc:`org.yamcs.mdb.DataDecoder`.
