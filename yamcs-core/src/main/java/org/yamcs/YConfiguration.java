package org.yamcs;

import static java.util.regex.Matcher.quoteReplacement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A configuration object is a wrapper around a Map&ltString, Object&gt which keeps track to a parent and its original
 * file (if any).
 * 
 * This class loads yamcs configurations. There are a number of "subsystems", each using a corresponding subsystem.yaml
 * file
 *
 * Configuration files are looked up in this order:
 * <ol>
 * <li>in the prefix/file.yaml via the classpath if the prefix is set in the setup method (used in the unittests)
 * <li>in the userConfigDirectory .yamcs/etc/file.yaml
 * <li>in the file.yaml via the classpath..
 * </ol>
 *
 * @author nm
 */
public class YConfiguration {

    public static File configDirectory; // This is used in client tools to overwrite
    static YConfigurationResolver resolver = new DefaultConfigurationResolver();
    static YConfigurationPropertyProvider propertyProvider = new DefaultPropertyProvider();

    private static Map<String, YConfiguration> configurations = new HashMap<>();
    static Logger log = LoggerFactory.getLogger(YConfiguration.class.getName());
    static String prefix = null;

    // keeps track of the configuration path so meaningful error messages can be printed
    // the path is something like filename.key1.subkey2[3]...
    // this is used for the old style when the methods of YConfiguration were called in a static way
    // Nowadays, please use Yconfiguration.getConfig() to make a child config, and then use the .path() to get the
    // similar path.
    private static IdentityHashMap<Object, String> staticConfPaths = new IdentityHashMap<>();

    private static final YConfiguration EMPTY_CONFIG = YConfiguration.wrap(Collections.emptyMap());

    public static final Pattern PROPERTY_PATTERN = Pattern
            .compile("\\$\\{((?<name>[\\w\\.\\-]+)(:(?<fallback>.*)?)?)\\}");

    /**
     * The parent configuration
     */
    YConfiguration parent;
    /**
     * The key by which this object can be located within its parent
     */
    String parentKey;
    /**
     * The root map
     */
    Map<String, Object> root;

    // this is set only for the root Yconfiguration (i.e. without a parent) and indicates where (which file) it has been
    // loaded from
    String rootLocation;

    private YConfiguration(String subsystem) throws IOException, ConfigurationException {
        this(subsystem, resolver.getConfigurationStream("/" + subsystem + ".yaml"), subsystem + ".yaml");
    }

    /**
     * Constructs a new configuration object parsing the input stream
     * 
     * @param is
     *            input stream where the configuration is loaded from
     * @param confpath
     *            configuration path - it is remembered together with the configuration in case of error to indicate
     *            where it is coming from (i.e. which file)
     */
    @SuppressWarnings("unchecked")
    public YConfiguration(String subsystem, InputStream is, String confpath) {
        this.rootLocation = confpath;
        Yaml yaml = getYamlParser();

        try {
            Object o = yaml.load(is);
            if (o == null) {
                o = new HashMap<String, Object>(); // config file is empty, not an error
            } else if (!(o instanceof Map<?, ?>)) {
                throw new ConfigurationException(confpath, "top level structure must be a map and not a " + o);
            }
            root = (Map<String, Object>) o;
            staticConfPaths.put(root, confpath);
        } catch (YAMLException e) {
            throw new ConfigurationException(confpath, e.toString(), e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        configurations.put(subsystem, this);
    }

    /**
     * Create a Yaml parser by taking into account some system properties.
     */
    private Yaml getYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        int maxAliases = Integer.parseInt(System.getProperty("org.yamcs.yaml.maxAliases", "200"));
        loaderOptions.setMaxAliasesForCollections(maxAliases);

        return new Yaml(loaderOptions);
    }

    /**
     * 
     * @param parent
     * @param parentKey
     * @param root
     */
    public YConfiguration(YConfiguration parent, String parentKey, Map<String, Object> root) {
        this.root = root;
        this.parent = parent;
        this.parentKey = parentKey;
    }

    /**
     * Sets up the Yamcs configuration system and loads the UTC-TAI offsets.
     * <p>
     * This method is intended for client tools and may store or use files from {@code ~/.yamcs}.
     */
    public synchronized static void setupTool() {
        File userConfigDirectory = new File(System.getProperty("user.home"), ".yamcs");
        setupTool(userConfigDirectory);
    }

    /**
     * Sets up the Yamcs configuration system and loads the UTC-TAI offsets.
     * <p>
     * This method is intended for client tools that wish to customize the default config directory.
     * 
     * @param configDirectory
     */
    public synchronized static void setupTool(File configDirectory) {
        if (System.getProperty("java.util.logging.config.file") == null) {
            try (InputStream in = resolver.getConfigurationStream("/logging.properties")) {
                LogManager.getLogManager().readConfiguration(in);
            } catch (Exception e) {
                // do nothing, the default java builtin logging is used
            }
        }

        TimeEncoding.setUp();

        YConfiguration.configDirectory = configDirectory;
        File logDir = new File(configDirectory, "log");
        if (!logDir.exists()) {
            if (logDir.mkdirs()) {
                System.out.println("Created directory: " + logDir);
            } else {
                System.err.println("Cannot create directory: " + logDir);
            }
        }
    }

    /**
     * Sets up the Yamcs configuration system and loads the UTC-TAI offsets.
     * <p>
     * This method is intended for use in unit and integration tests. It allows resolving configuration files from a
     * specific subdirectory of the classpath.
     *
     * @param configPrefix
     *            the name of the subdirectory where to resolve configuration files. This is resolved from the
     *            classpath.
     */
    public static synchronized void setupTest(String configPrefix) {
        prefix = configPrefix;
        configurations.clear(); // forget any known config (useful in the maven unit tests called in the same VM)
        resolver = new DefaultConfigurationResolver();

        if (System.getProperty("java.util.logging.config.file") == null) {
            try (InputStream in = resolver.getConfigurationStream("/logging.properties")) {
                LogManager.getLogManager().readConfiguration(in);
            } catch (Exception e) {
                // do nothing, the default java builtin logging is used
            }
        }

        TimeEncoding.setUp();
    }

    public static synchronized void clearConfigs() {
        configurations.clear();
    }

    /**
     * Loads (if not already loaded) and returns a configuration corresponding to a file &lt;subsystem&gt;.yaml
     *
     * This method does not reload the configuration file if it has changed.
     *
     * @param subsystem
     * @return the loaded configuration
     * @throws ConfigurationException
     *             if the configuration file could not be found or not loaded (e.g. error in yaml formatting)
     */
    public synchronized static YConfiguration getConfiguration(String subsystem) throws ConfigurationException {
        if (subsystem.contains("..") || subsystem.contains("/")) {
            throw new ConfigurationException("Invalid subsystem '" + subsystem + "'");
        }
        YConfiguration c = configurations.get(subsystem);
        if (c == null) {
            try {
                c = new YConfiguration(subsystem);
            } catch (IOException e) {
                throw new ConfigurationException("Cannot load configuration for subsystem " + subsystem + ": " + e);
            }
            configurations.put(subsystem, c);
        }
        return c;
    }

    /**
     * Loads and returns a configuration corresponding to a file &lt;subsystem&gt;.yaml
     *
     * This method reloads the configuration file always.
     *
     * @param subsystem
     * @param reload
     * @return the loaded configuration
     * @throws ConfigurationException
     *             if the configuration file could not be found or not loaded (e.g. error in yaml formatting)
     */
    public synchronized static YConfiguration getConfiguration(String subsystem, boolean reload)
            throws ConfigurationException {
        if (reload) {
            YConfiguration c = configurations.get(subsystem);
            if (c != null) {
                configurations.remove(subsystem);
            }
        }
        return getConfiguration(subsystem);
    }

    public static boolean isDefined(String subsystem) throws ConfigurationException {
        try {
            getConfiguration(subsystem);
            return true;
        } catch (ConfigurationNotFoundException e) {
            return false;
        }
    }

    public static boolean isNull(Map<?, ?> m, String key) {
        if (!m.containsKey(key)) {
            throw new ConfigurationException(staticConfPaths.get(m), "cannot find a mapping for key '" + key + "'");
        }
        Object o = m.get(key);
        return o == null;
    }

    private void checkKey(String key, Class<?> cls) throws ConfigurationException {
        if (!root.containsKey(key)) {
            throw new ConfigurationException(getPath(), "cannot find a mapping for key '" + key + "'");
        }
        Object o = root.get(key);
        if (o == null) {
            throw new ConfigurationException(getPath(), key + " exists but is null");
        }
        if (!cls.isInstance(o)) {
            throw new ConfigurationException(getPath(), key + " is not of the expected type " + cls.getName());
        }
    }

    private static void checkKey(Map<String, Object> m, String key) throws ConfigurationException {
        if (!m.containsKey(key)) {
            throw new ConfigurationException(staticConfPaths.get(m), "cannot find a mapping for key '" + key + "'");
        } else if (m.get(key) == null) {
            throw new ConfigurationException(staticConfPaths.get(m), key + " exists but is null");
        }
    }

    public boolean containsKey(String key) {
        return root.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(String key, String key1) throws ConfigurationException {
        if (!root.containsKey(key)) {
            return false;
        }
        checkKey(key, Map.class);
        Map<String, Object> m = (Map<String, Object>) root.get(key);
        return m.containsKey(key1);
    }

    /**
     * returns the first entry in the config file if it's a map. Otherwise throws an error
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFirstMap() throws ConfigurationException {
        Object o = root.values().iterator().next();
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        } else {
            throw new ConfigurationException(
                    "the first entry in the config is of type " + o.getClass() + " and not Map");
        }
    }

    /**
     * returns the first entry(key) in the config file.
     * 
     * @return
     */
    public String getFirstEntry() throws ConfigurationException {
        return root.keySet().iterator().next();
    }

    public Set<String> getKeys() {
        return root.keySet();
    }

    private static String getUnqualifiedClassName(Object o) {
        String name = o.getClass().getName();
        if (name.lastIndexOf('.') > 0) {
            name = name.substring(name.lastIndexOf('.') + 1); // Map$Entry
        }
        // The $ can be converted to a .
        name = name.replace('$', '.'); // Map.Entry
        return name;
    }

    public Map<String, Object> getRoot() {
        return root;
    }

    /**
     * If the key is pointing to a map, creates and returns a configuration object out of that map
     * <p>
     * The returned object will have its parent set to this object
     * <p>
     * If the key does not exist a ConfigurationException is thrown.
     *
     * @param key
     * @return
     */
    public YConfiguration getConfig(String key) {
        Map<String, Object> m = getMap(key);
        return new YConfiguration(this, key, m);
    }

    /**
     * Same as {@link #getConfig(String)} but return an empty config if the key does not exist.
     *
     * @param key
     * @return
     */
    public YConfiguration getConfigOrEmpty(String key) {
        if (root.containsKey(key) && root.get(key) != null) {
            return getConfig(key);
        } else {
            return YConfiguration.emptyConfig();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Map) {
            Map<String, Object> m1 = (Map<String, Object>) o;
            if (staticConfPaths.containsKey(m1)) {
                staticConfPaths.put(m1, staticConfPaths.get(m) + "->" + key);
            }
            return m1;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + o.getClass().getCanonicalName() + " and not Map");
        }
    }

    /**
     * 
     * Consider using {@link #getConfig} to get a child config instead of accessing the map directly
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key) throws ConfigurationException {
        checkKey(key, Map.class);
        return (Map<K, V>) root.get(key);
    }

    public Map<String, Object> getSubMap(String key, String key1) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getMap(m, key1);
    }

    /**
     * Returns m.get(key) if it exists and is of type string, otherwise throws an exception
     * 
     * @param m
     * @param key
     * @return
     * @throws ConfigurationException
     */
    public static String getString(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);

        Object o = m.get(key);
        if (o instanceof String) {
            return expandString(staticConfPaths.get(m), (String) o);
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + getUnqualifiedClassName(o) + " and not String");
        }
    }

    public static String getString(Map<String, Object> m, String key, String defaultValue)
            throws ConfigurationException {
        if (m.containsKey(key)) {
            return getString(m, key);
        } else {
            return defaultValue;
        }
    }

    public String getString(String key) throws ConfigurationException {
        return getString(root, key);
    }

    public String getString(String key, String defaultValue) throws ConfigurationException {
        return getString(root, key, defaultValue);
    }

    /*
     * The key has to point to a map that contains the subkey that points to a string
     */
    public String getSubString(String key, String subkey) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getString(m, subkey);
    }

    static String expandString(String confPath, String property) {
        // Expand a system property like ${foo} or an environment property like ${env.foo}
        String expanded = property;
        while (expanded.contains("${")) {
            StringBuilder buf = new StringBuilder();
            Matcher matcher = PROPERTY_PATTERN.matcher(expanded);
            while (matcher.find()) {
                String name = matcher.group("name");

                String replacement = null;
                if (name.startsWith("env.")) {
                    replacement = System.getenv(name.substring(4));
                } else {
                    replacement = propertyProvider.get(name);
                }

                if (replacement == null) {
                    replacement = matcher.group("fallback");
                }
                if (replacement == null) {
                    throw new ConfigurationException(confPath, "cannot resolve property '" + name + "'");
                }
                matcher.appendReplacement(buf, quoteReplacement(replacement));
            }
            matcher.appendTail(buf);
            expanded = buf.toString();
        }
        return expanded;
    }

    /*
     * The key has to point to a list
     */
    public <T> List<T> getList(String key) throws ConfigurationException {
        return getList(root, key);
    }

    @SuppressWarnings("unchecked")
    public List<YConfiguration> getConfigList(String key) throws ConfigurationException {
        checkKey(root, key);
        List<YConfiguration> r = new ArrayList<>();
        Object o = root.get(key);
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            for (int i = 0; i < l.size(); i++) {
                Object o1 = l.get(i);
                if (o1 instanceof Map) {
                    r.add(new YConfiguration(this, key + "[" + i + "]", (Map<String, Object>) o1));
                } else {
                    throw new ConfigurationException(this, "One element of the list is not a map: " + o1);
                }
            }
        } else {
            throw new ConfigurationException(staticConfPaths.get(root),
                    "mapping for key '" + key + "' is of type " + getUnqualifiedClassName(o) + " and not List");
        }
        return r;
    }

    public double getDouble(String key) throws ConfigurationException {
        return getDouble(root, key);
    }

    public double getDouble(String key, double defaultValue) throws ConfigurationException {
        if (!root.containsKey(key)) {
            return defaultValue;
        }
        return getDouble(key);
    }

    /**
     * This is the same like the method above but will create a {class: "string"} for strings rather than throwing an
     * exception. It is to be used when loading service list which can be specified just by the class name.
     * 
     * @param key
     * @return
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    public List<YConfiguration> getServiceConfigList(String key) throws ConfigurationException {
        checkKey(root, key);
        List<YConfiguration> r = new ArrayList<>();
        Object o = root.get(key);
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            for (int i = 0; i < l.size(); i++) {
                Object o1 = l.get(i);
                if (o1 instanceof Map) {
                    r.add(new YConfiguration(this, key + "[" + i + "]", (Map<String, Object>) o1));
                } else if (o1 instanceof String) {
                    Map<String, Object> m1 = new HashMap<>();
                    m1.put("class", o1);
                    r.add(new YConfiguration(this, key + "[" + i + "]", (Map<String, Object>) m1));
                } else {
                    throw new ConfigurationException(this, "One element of the list is not a map: " + o1);
                }
            }
        } else {
            throw new ConfigurationException(staticConfPaths.get(root),
                    "mapping for key '" + key + "' is of type " + getUnqualifiedClassName(o) + " and not List");
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getSubList(String key, String key1) throws ConfigurationException {
        checkKey(key, Map.class);
        Map<String, Object> m = (Map<String, Object>) root.get(key);
        return getList(m, key1);
    }

    /**
     * Returns m.get(key) if it exists and is of type boolean, if m.get(key) exists and is not boolean, throw an
     * exception. if m.get(key) does not exist, return the default value.
     * 
     * @param m
     * @param key
     * @param defaultValue
     *            - the default value to return if m.get(key) does not exist.
     * @return the boolean config value
     * @throws ConfigurationException
     */
    public static boolean getBoolean(Map<String, Object> m, String key, boolean defaultValue)
            throws ConfigurationException {
        if (m.containsKey(key)) {
            return getBoolean(m, key);
        } else {
            return defaultValue;
        }
    }

    public static boolean getBoolean(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Boolean) {
            return (Boolean) o;
        } else if (o instanceof String) {
            String stringValue = getString(m, key);
            switch (stringValue) {
            case "yes":
            case "true":
            case "on":
                return true;
            case "no":
            case "false":
            case "off":
                return false;
            }
        }
        throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                + getUnqualifiedClassName(o) + " and not Boolean (use true or false without quotes)");
    }

    public boolean getBoolean(String key) throws ConfigurationException {
        return getBoolean(root, key);
    }

    public boolean getBoolean(String key, String key1) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getBoolean(m, key1);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(root, key, defaultValue);
    }

    public int getInt(String key) throws ConfigurationException {
        return getInt(root, key);
    }

    public int getInt(String key, int defaultValue) throws ConfigurationException {
        return getInt(root, key, defaultValue);
    }

    public int getInt(String key, String key1) throws ConfigurationException {
        Map<String, Object> m = getMap(key);
        return getInt(m, key1);
    }

    public int getInt(String key, String key1, int defaultValue) throws ConfigurationException {
        if (!root.containsKey(key)) {
            return defaultValue;
        }

        Map<String, Object> m = getMap(key);

        return getInt(m, key1, defaultValue);
    }

    public static int getInt(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Integer) {
            return (Integer) o;
        } else if (o instanceof String) {
            String stringValue = getString(m, key);
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        throw new ConfigurationException(staticConfPaths.get(m),
                "mapping for key '" + key + "' is of type " + getUnqualifiedClassName(o) + " and not Integer");
    }

    /**
     * return the m.get(key) as an int if it's present or v if it is not.
     *
     * If the key is present but the value is not an integer, a ConfigurationException is thrown.
     * 
     * @param m
     * @param key
     * @param defaultValue
     * @return the value from the map or the passed value if the map does not contain the key
     * @throws ConfigurationException
     *             if the key is present but it's not an int
     */
    public static int getInt(Map<String, Object> m, String key, int defaultValue) throws ConfigurationException {
        if (!m.containsKey(key)) {
            return defaultValue;
        } else {
            return getInt(m, key);
        }
    }

    public long getLong(String key) {
        return getLong(root, key);
    }

    public long getLong(String key, long defaultValue) {
        return getLong(root, key, defaultValue);
    }

    public static long getLong(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Integer) {
            return (Integer) o;
        } else if (o instanceof Long) {
            return (Long) o;
        } else if (o instanceof String) {
            String stringValue = getString(m, key);
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                + getUnqualifiedClassName(o) + " and not Integer or Long");
    }

    public byte[] getBinary(String key) {
        return getBinary(root, key);
    }

    public byte[] getBinary(String key, byte[] defaultValue) {
        if (root.containsKey(key)) {
            return getBinary(root, key);
        } else {
            return defaultValue;

        }
    }

    public static byte[] getBinary(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof byte[]) {
            return (byte[]) o;
        } else if (o instanceof String) {
            String stringValue = getString(m, key);
            try {
                return StringConverter.hexStringToArray((String) o);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("'" + stringValue + "' is not a hexadecimal string");
            }
        } else {
            throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                    + getUnqualifiedClassName(o) + " and not binary or hexadecimal string");
        }
    }

    /**
     * return the m.get(key) as an long if it's present or v if it is not.
     *
     * @param m
     * @param key
     * @param v
     * @return the value from the map or the passed value if the map does not contain the key
     * @throws ConfigurationException
     *             if the key is present but it's not an long
     */
    public static long getLong(Map<String, Object> m, String key, long v) throws ConfigurationException {
        if (!m.containsKey(key)) {
            return v;
        } else {
            return getLong(m, key);
        }
    }

    public static double getDouble(Map<String, Object> m, String key, double v) throws ConfigurationException {
        if (!m.containsKey(key)) {
            return v;
        } else {
            return getDouble(m, key);
        }
    }

    public static double getDouble(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        } else if (o instanceof String) {
            String stringValue = getString(m, key);
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        throw new ConfigurationException(staticConfPaths.get(m), "mapping for key '" + key + "' is of type "
                + getUnqualifiedClassName(o) + " and not Double");
    }

    public boolean isList(String key) {
        return isList(root, key);
    }

    public static boolean isList(Map<String, Object> m, String key) {
        checkKey(m, key);
        Object o = m.get(key);
        return (o instanceof List);
    }

    public static void setResolver(YConfigurationResolver resolver) {
        YConfiguration.resolver = resolver;
    }

    public static YConfigurationResolver getResolver() {
        return YConfiguration.resolver;
    }

    public static void setPropertyProvider(YConfigurationPropertyProvider propertyProvider) {
        YConfiguration.propertyProvider = propertyProvider;
    }

    public static YConfigurationPropertyProvider getPropertyProvider() {
        return YConfiguration.propertyProvider;
    }

    /**
     * Default config file resolver. Looks for configuration files in the classpath and in the user config directory
     * (~/.yamcs/).
     */
    public static class DefaultConfigurationResolver implements YConfigurationResolver {
        @Override
        public InputStream getConfigurationStream(String name) throws ConfigurationException {
            InputStream is;
            if (prefix != null) {
                if ((is = YConfiguration.class.getResourceAsStream("/" + prefix + name)) != null) {
                    log.debug("Reading {}", new File(YConfiguration.class.getResource("/" + prefix + name).getFile())
                            .getAbsolutePath());
                    return is;
                }
            }

            // see if the users has an own version of the file
            if (configDirectory != null) {
                File f = new File(configDirectory, name);
                if (f.exists()) {
                    try {
                        is = new FileInputStream(f);
                        log.debug("Reading {}", f.getAbsolutePath());
                        return is;
                    } catch (FileNotFoundException e) {
                        throw new ConfigurationException("Cannot read file " + f, e);
                    }
                }
            }

            is = YConfiguration.class.getResourceAsStream(name);
            if (is == null) {
                throw new ConfigurationNotFoundException("Cannot find resource " + name);
            }
            log.debug("Reading {}", new File(YConfiguration.class.getResource(name).getFile()).getAbsolutePath());
            return is;
        }
    }

    /**
     * Default property provider. Looks up values with {@link System#getProperty(String)}.
     */
    public static class DefaultPropertyProvider implements YConfigurationPropertyProvider {
        @Override
        public String get(String name) {
            return System.getProperty(name);
        }
    }

    /**
     * Introduced to be able to detect when a configuration file was not specified (as opposed to when there's a
     * validation error inside). The current default behaviour of Yamcs is to throw an error when
     * getConfiguration(String subystem) is called and the resource does not exist.
     */
    public static class ConfigurationNotFoundException extends ConfigurationException {
        private static final long serialVersionUID = 1L;

        public ConfigurationNotFoundException(String message) {
            super(message);
        }

        public ConfigurationNotFoundException(String message, Throwable t) {
            super(message, t);
        }
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass) {
        return getEnum(root, key, enumClass);
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        if (root.containsKey(key)) {
            return getEnum(root, key, enumClass);
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns a value of an enumeration that matches ignoring case the string obtained from the config with the given
     * key. Throws an Configurationexception if the key does not exist in config or if it does not map to a valid
     * enumeration value
     * 
     * @param config
     * @param key
     * @param enumClass
     * @return
     */
    public static <T extends Enum<T>> T getEnum(Map<String, Object> config, String key, Class<T> enumClass) {
        String sk = getString(config, key);

        T[] values = enumClass.getEnumConstants();
        for (T v : values) {
            if (v.toString().equalsIgnoreCase(sk)) {
                return v;
            }
        }
        throw new ConfigurationException("Invalid value '" + sk + "'. Valid values are: " + Arrays.toString(values));
    }

    /**
     * 
     * @param key
     * @return root.get(key)
     */
    public Object get(String key) {
        Object value = root.get(key);
        if (value instanceof String) {
            return getString(key); // Expand properties
        } else {
            return value;
        }
    }

    /**
     * Create a new configuration wrapping around a map The resulting config will have no parent
     * 
     * @param m
     * @return
     */
    public static YConfiguration wrap(Map<String, Object> m) {
        return new YConfiguration(null, null, m);
    }

    public static YConfiguration emptyConfig() {
        return EMPTY_CONFIG;
    }

    public Map<String, Object> toMap() {
        return getRoot();
    }

    public String getPath() {
        if (parent == null) {
            return rootLocation;
        }

        StringBuilder sb = new StringBuilder();
        buildPath(this, sb);
        return sb.toString();
    }

    private static void buildPath(YConfiguration c, StringBuilder sb) {
        if (c.parent != null) {
            buildPath(c.parent, sb);
            if (c.parent.parent != null) {
                sb.append(".");
            }
            sb.append(c.parentKey);
        } else {
            sb.append(c.rootLocation).append(": ");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getList(Map<String, Object> m, String key) throws ConfigurationException {
        checkKey(m, key);
        Object o = m.get(key);
        if (o instanceof List) {
            List<T> l = new ArrayList<>((List<T>) o);
            String parentPath = staticConfPaths.get(m);
            for (int i = 0; i < l.size(); i++) {
                Object o1 = l.get(i);
                if (!staticConfPaths.containsKey(o1)) {
                    staticConfPaths.put(o1, parentPath + "->" + key + "[" + i + "]");
                }
                if (o1 instanceof String) {
                    String confPath = staticConfPaths.get(o1);
                    l.set(i, (T) expandString(confPath, (String) o1));
                }
            }
            return l;
        } else {
            throw new ConfigurationException(staticConfPaths.get(m),
                    "mapping for key '" + key + "' is of type " + getUnqualifiedClassName(o) + " and not List");
        }
    }

    @Override
    public String toString() {
        return root.toString();
    }

    /**
     * If config.get(key) exists and is a list, and the list has the element idx and is a map, then return a
     * configuration wrapper around that map.
     * <p>
     * Otherwise throw a ConfigurationException
     * 
     * @param key
     * @param idx
     * @return
     */
    @SuppressWarnings("unchecked")
    public YConfiguration getConfigListIdx(String key, int idx) {
        checkKey(root, key);
        Object o = root.get(key);
        if (!(o instanceof List)) {
            throw new ConfigurationException(staticConfPaths.get(root),
                    "mapping for key '" + key + "' is of type " + getUnqualifiedClassName(o) + " and not List");
        }

        List<?> l = (List<?>) o;
        if (idx >= l.size()) {
            throw new ConfigurationException(staticConfPaths.get(root),
                    "mapping for key '" + key + "' is a list but the requested index " + idx
                            + " is outside of the list");
        }

        Object o1 = l.get(idx);
        if (!(o1 instanceof Map)) {
            throw new ConfigurationException(this,
                    "The element " + idx + " in the list is not a map but " + getUnqualifiedClassName(o1));
        }

        return new YConfiguration(this, key + "[" + idx + "]", (Map<String, Object>) o1);
    }
}
