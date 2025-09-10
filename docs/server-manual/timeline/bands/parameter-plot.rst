Parameter Plot
==============

With this band type you can add parameter plots to a timeline view. Each band of this type, can show multiple parameter traces at the same time, on a common Y-axis. The Y-axis shows tick labels for the lowest and the higest value of the value scale.


Configuration Options
---------------------

Frozen
    If enabled, this band is always visible, even when scrolling down. Frozen bands always rendered above non-frozen bands.

Height
    Band height in pixels

Minimum
    Minimum value on the Y-axis. Values lower than the minimum are not visible. If undefined, the value is automatically derived from the actual trace data.

Maximum
    Maximum value on the Y-axis. Values higher than the maximum are not visible. If undefined, the value is automatically derived from the actual trace data.

Zero line width
    Pixel thickness of the zero line, a horizontal line marking the value 0.

    Set to 0 if you do not want a zero line.

Zero line color
    RGB color for the zero line

Minimum fraction digits
    The minimum number of fraction digits to use. This is considered when rendering labels on the Y-axis, or when showing values in hover tooltips.

Maximum fraction digits
    The maximum number of fraction digits to use. This is considered when rendering labels on the Y-axis, or when showing values in hover tooltips.


Trace Options
-------------

At least one trace must be added. The order in which traces are defined is also the order in which they are rendered, which can be important when you use fill effects.

Parameter
    Fully-qualified name of the parameter to be displayed by this trace.

Line color
    RGB color for this trace

Visible
    Toggle whether this trace is visible. Useful to temporarily hide a trace, without losing its definition.

Line width
    Pixel thickness of the trace line.

Show min/max
    Toggle whether to show min/max values over the time range. Plot points are the result of a downsampling algorithm on the raw archive, so depending on the point selection and the zoom level, the min/max values may provide you with more insight on the actual value range.

    Min/max zones are filled in the same color as the line, but with a configurable opacity.

Min/max opacity
    Opacity of the min/max zones. Value between 0 and 1. 0 means fully transparent.

    This property is only visible when **Show min/max** is enabled.

Fill
    Toggle whether to fill the area between the trace and the zero line.

Fill color
    RGB color for the fill between the trace and the zero line.

    This property is only visible when **Fill** is enabled.
