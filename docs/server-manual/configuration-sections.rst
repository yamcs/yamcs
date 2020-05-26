Configuration Sections
======================

You can add support for custom configuration options inside the main ``yamcs.yaml`` file. This is called a configuration section. Sections are represented by a top-level identifier.

Configuration sections should be added from the constructor of a custom Yamcs plugin. This ensures they are added only once, and will allow Yamcs to properly validate server configuration.

As an example, the ``yamcs-web`` module is packaged as a Yamcs plugin, and has this class:

.. code-block:: java

    public class WebPlugin implements Plugin {

        public WebPlugin {
            Spec spec = new Spec();
            // ...


            YamcsServer yamcs = YamcsServer.getServer();
            yamcs.addConfigurationSection("yamcs-web", spec);
        }

        @Override
        public void onLoad() throws PluginException {
            // Retrieve the actual configuration
            YConfiguration yamcsConfig = YamcsServer.getServer().getConfig();
            YConfiguration config = YConfiguration.emptyConfig();
            if (yamcsConfig.containsKey("yamcs-web")) {
                config = yamcsConfig.getConfig("yamcs-web");
            }
        }
    }

Here the :javadoc:`org.yamcs.Spec` object is a helper class that allows definining how to validate your plugin configuration. Yamcs will take care of the actual validation step, and if all went well the ``onLoad`` hook should trigger. This is a good place to access the runtime configuration model, and retrieve your custom options.

If you have custom components that want to access this configuration, one possible way is to provide accessors on your plugin class, and then to retrieve the singleton instance of your plugin class:

.. code-block:: java

    PluginManager pluginManager = YamcsServer.getServer().getPluginManager();
    MyPlugin plugin = pluginManager.getPlugin(MyPlugin.class);
    // ...
