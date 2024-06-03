Mission database
================

The MDB module within the Yamcs web interface provides a set of views on the Mission Database.

The MDB Module is always visited for a specific Yamcs instance. The MDB for an instance aggregates the content of the entire MDB loader tree for that instance.


Parameters
----------

The Parameters view shows a filterable list of all parameters inside the MDB. If you are searching for a specific parameter but don't remember the space system this views can help find it quickly.

You can navigate to the detail page of any parameter to see a quick look at its definition, and to see the current realtime value. If the parameter has numeric values, its data can also be rendered on a chart. This chart is updated in realtime. Finally the detail page of a parameter also has a view that allows looking at the exact data points that have been received in a particular time range. This information is presented in a paged view. There is a download option available for downloading data points of the selected time range as a CSV file for offline analysis.

If the parameter is a software parameter, its value can be set via a button in the toolbar.


Containers
----------

The Containers view shows a filterable list of all containers inside the MDB. The detail page allows seeing the parameter or container entries for this container and offers navigation links for quick access.


Commands
--------

The Commands view shows a filterable list of all commands inside the MDB. This also includes abstract commands. Non-abstract commands can be issued directly from the detail page of that command. This opens a dynamic dialog window where you can override default arguments and enter missing arguments.


Algorithms
----------

The Algorithms view shows a filterable list of all algorithms inside the MDB. This detail page provides a quick navigation list of all input and output parameters and shows the script for this algorithm.
