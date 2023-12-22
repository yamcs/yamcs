Algorithms Sheet
================

This sheet contains arbitrarily complex user algorithms that can set (derived) output parameters based on any number of input parameters.

Empty lines are used to separate algorithms and cannot be used inside the specification of one algorithm.

The column names are:


``algorithm name``
    The name of the algorithm within the space system.

``language``
    The programming language of the algorithm. Currently supported values are JavaScript, python and java.

    ``python`` requires adding the `jython-standalone <https://mvnrepository.com/artifact/org.python/jython-standalone>` jar to the Java classpath (due to its large size, this is by default not included in Yamcs distributions).

``text``
    The code of the algorithm. See: :doc:`../../algorithm-definitions`.

``trigger``
    Optionally specify when the algorithm should trigger:

    * ``OnParameterUpdate('/some-param', 'some-other-param')`` Execute the algorithm whenever *any* of the specified parameters are updated
    * ``OnInputParameterUpdate`` This is the same as above for all input parameters (i.e. execute whenever *any* input parameter is updated).
    * ``OnPeriodicRate(<fireRate>)`` Execute the algorithm every ``fireRate`` milliseconds
    * ``none`` The algorithm doesn't trigger automatically but can be called upon from other parts of the system (like the command verifier)

    The default is none.

``in/out``
    Whether a parameter is inputted to, or outputted from the algorithm. Parameters are defined, one per line, following the line defining the algorithm name.

``parameter reference``
    Algorithms can be interdependent, meaning that the output parameters of one algorithm could be used as input parameters of another algorithm.

``instance``
    Allows inputting a specific instance of a parameter. At this stage, only values smaller than or equal to zero are allowed. A negative value, means going back in time. Zero is the default and means the actual value. This functionality allows for time-based window operations over multiple packets. Algorithms with windowed parameters will only trigger as soon as all of those parameters have all instances defined (i.e. when the windows are full).

    Note that this column should be left empty for output parameters.

``variable name``
    An optional friendlier name for use in the algorithm. By default the parameter name is used, which may lead to runtime errors depending on the naming conventions of the applicable script language.

    Note that a unique name is required in this column, when multiple instances of the same parameter are inputted.

``flags``
    This column is applicable for each ``in`` parameter and can have the following values:
 
    ``M``
        Short for mandatory. The algorithm will not trigger unless a value is set for this input parameter.

``description``
    Textual description of the algorithm. Should be one line.

``long description``
    Long textual description of the algorithm. In Markdown format.

``namespace:<ALIAS>``
    Any numbers of namespace columns can be added using the prefix ``namespace:`` followed by the name of a namespace.

    This allows associating alternative names to algorithms.
