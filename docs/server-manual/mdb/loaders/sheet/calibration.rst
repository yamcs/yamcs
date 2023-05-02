Calibration Sheet
=================

This sheet contains calibration data including enumerations. It has the following columns:

``calibrator name`` (required)
    Name of the calibration. Used as a reference in the :doc:`Parameters <parameters>` or :doc:`Commands <commands>` sheet.

``type`` (required)
    One of ``polynomial``, ``spline``, ``enumeration``, ``java-expression`` or ``time``.

    Detailed in sections below.

    * ``time`` for converting a raw integer or float value into a timestamp value.

``calib1``
    Contents depends on the chosen ``type``. See sections below.

``calib2``
    Contents depends on the chosen ``type``. See sections below.


Polynomials
^^^^^^^^^^^

If the type is set to ``polynomial``, polynomial calibration is performed.

``calib1`` (required)
    List the coefficients, one per row starting with the constant and up to the highest grade. There is no limit in the number of coefficients (i.e. order of polynomial).

``calib2``
    (not used)

Note that the polynomial calibration is performed with double precision floating point numbers even though the input and/or output may be 32 bit.


Splines
^^^^^^^

If the type is set to ``spline``, linear spline (pointpair) interpolation is performed. As with polynomial calibration, the computation is performed with double precision numbers.

``calib1`` (required)
    Start point: ``x`` from ``(x, y)`` pair.

``calib2`` (required)
    Stop point: ``y`` from ``(x, y)`` pair.


Enumerations
^^^^^^^^^^^^

If the type is set to ``enumeration``, the calibrator can be used to map enumeration states.

``calib1`` (required)
    Numeric value

``calib2`` (required)
    Text state corresponding to ``calib1``.


Java Expressions
^^^^^^^^^^^^^^^^

The type ``java-expression`` serves as a catch-all. They can be used for float or integer calibrations.

``calib1`` (required)
    The textual formula to be executed. This expression will be enclosed and compiled into a class like this:

    .. code-block:: java

        package org.yamcs.mdb.jecf;
        public class Expression665372494 implements org.yamcs.mdb.CalibratorProc {
            public double calibrate(double rv) {
                    return <expression>;
            }
        }

    The expression should return a double, but Java will convert implicitly any other primitive type to a double.

    Java statements cannot be used, however the ternary operator ``? :`` can be used; for example this expression would compile fine:

    .. code-block:: java

        rv > 0 ? rv + 5 : rv - 5

    Static functions can be also referenced. In addition to the usual Java ones (e.g. ``Math.sin``, ``Math.log``, etc) user-own functions (available in the Java classpath) can be referenced by specifying the full class name:

    .. code-block:: java

        my.very.complicated.calibrator.Execute(rv)

``calib2``
    (not used)


Time
^^^^

If the type is ``time``, this calibrator allows to convert a raw integer or float value into a timestamp value by using the raw value as an offset from a well known epoch or from another parameter. Optionally allow to use an (offset:scale) which can be used to scale the raw value from other units (e.g. millseconds) to seconds.

Known epochs are ``GPS``, ``TAI``, ``UNIX`` and ``J2000``.

The conversion is performed as follows:

* When using a known epoch: ``engValue = <epoch>_yamcs_difference + offset+rawValue*scale``.
* When using another parameter ``p``: ``engValue = p.engValue + offset+rawValue*scale``.


``calib1`` (required)
    Something of the shape ``epoch:<epoch>`` or ``parameter:<parameter reference>``. The reference has to be to a parameter of type ``time``.

``calib2``
    Optionally something of the shape ``offset:scale`` where both offset and scale are numbers.

    If unset, this defaults to ``0:1``
