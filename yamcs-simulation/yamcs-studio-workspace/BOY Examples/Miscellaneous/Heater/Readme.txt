Example for Water Tank Heater as used in some EPICS
Database introductions.

Requires an EPICS base installation with a 'softIoc'
command to execute the EPICS database files like this:

  softIoc -m user=demo -s -d tank.db -d control.db
  
The display files need the same macro user=....

Define the macro either in a screen that calls the heater.opi file,
or set as a BOY preference:
CSS/Preferences/CSS Applications/Display/BOY/Runtime
