Algorithms Sheet
================

This sheet contains arbitrarily complex user algorithms that can set (derived) output parameters based on any number of input parameters.

Comment lines starting with “#” on the first column can appear everywhere and are ignored.
Empty lines are used to separate algorithms and cannot be used inside the specification of one algorithm.


algorithm name
    The identifying name of the algorithm.

algorithm language
    The programming language of the algorithm. Currently supported values are JavaScript, python and Java.

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
    Algorithms can be interdependent, meaning that the output parameters of one algorithm could be used as input parameters of another algorithm.

instance
    Allows inputting a specific instance of a parameter. At this stage, only values smaller than or equal to zero are allowed. A negative value, means going back in time. Zero is the default and means the actual value. This functionality allows for time-based window operations over multiple packets. Algorithms with windowed parameters will only trigger as soon as all of those parameters have all instances defined (i.e. when the windows are full).

    Note that this column should be left empty for output parameters.

name used in the algorithm
    An optional friendlier name for use in the algorithm. By default the parameter name is used, which may lead to runtime errors depending on the naming conventions of the applicable script language.

    Note that a unique name is required in this column, when multiple instances of the same parameter are inputted.

See :doc:`../../algorithm-definitions`
