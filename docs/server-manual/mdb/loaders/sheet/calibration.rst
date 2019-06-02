Calibration Sheet
=================

This sheet contains calibration data including enumerations.

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
