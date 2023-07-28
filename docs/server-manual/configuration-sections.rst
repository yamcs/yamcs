Configuration Sections
======================

Some of the standard configuration files can be extended with custom configuration options. This is called a configuration section. Sections are represented by a top-level identifier and are scoped to a type of configuration file.

A Yamcs plugin is automatically associated with a configuration section named after the plugin identifier.

For example, the ``yamcs-web`` module is packaged as a Yamcs plugin, and accepts configuration options read from the ``yamcs-web`` section of the main :file:`etc/yamcs.yaml`:

.. code-block:: java

    public class WebPlugin implements Plugin {

        public Spec getSpec() {
            Spec spec = new Spec();
            // ...
            return spec;
        }

        @Override
        public void onLoad(YConfiguration config) throws PluginException {
            // Use the actual configuration
        }
    }

Here the :javadoc:`org.yamcs.Spec` object is a helper class that allows defining how to validate your plugin configuration. Yamcs will take care of the actual validation step, and if all went well the ``onLoad`` hook should trigger. This is a good place to access the runtime configuration model, and retrieve your custom options.

If you have custom components that want to access this configuration, one possible way is to provide accessors on your plugin class, and then to retrieve the singleton instance of your plugin class:

.. code-block:: java

    PluginManager pluginManager = YamcsServer.getServer().getPluginManager();
    MyPlugin plugin = pluginManager.getPlugin(MyPlugin.class);
    // ...


.. rubric:: Instance-specific configuration

Besides global plugin configuration options in :file:`etc/yamcs.yaml`, you may also want to add instance-specific configuration options. These would be considered when validating any :file:`etc/yamcs.{instance}.yaml` file:

.. code-block:: java

    YamcsServer yamcs = YamcsServer.getServer();
    yamcs.addConfigurationSection(ConfigScope.YAMCS_INSTANCE, "my-section", spec);
