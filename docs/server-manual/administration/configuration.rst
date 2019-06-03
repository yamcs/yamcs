Configuration
=============

Yamcs configuration files are written in YAML format. This format allows to encode in a human friendly way the most common data types: numbers, strings, lists and maps. For detailed syntax rules, please see https://yaml.org.

The root configuration file is ``etc/yamcs.yaml``. It contains a list of Yamcs instances. For each instance, a file called ``etc/yamcs.instance-name.yaml`` defines all the components that are part of the instance. Depending on which components are selected, different configuration files are needed.
