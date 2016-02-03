package org.yamcs.derivedvalues;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;


public class DerivedValues_XTCE implements DerivedValuesProvider {
    private static final Logger log = LoggerFactory.getLogger(DerivedValues_XTCE.class);
    ArrayList<DerivedValue> derivedValues = new ArrayList<DerivedValue>();
    int algoCount;
    String currentCode, currentOutputParam, currentAlgo;
    String[] currentInputParams;
    File currentXtceDir;
    ScriptEngineManager semgr;
    ScriptEngine currentEngine;
    HashMap<String, ScriptEngine> enginesByName = new HashMap<String, ScriptEngine>();
    HashMap<String, ScriptEngine> enginesByExtension = new HashMap<String, ScriptEngine>();
    private XtceDb xtcedb;
    String ssName;

    public DerivedValues_XTCE(XtceDb xtcedb) {
	this.xtcedb = xtcedb;

	// Load scripting engines
	semgr = new ScriptEngineManager();
	for (ScriptEngineFactory factory : semgr.getEngineFactories()) {
	    log.debug(String
		    .format("Loading scripting engine: %s/%s, language: %s/%s, names: %s, extensions: %s",
			    factory.getEngineName(),
			    factory.getEngineVersion(),
			    factory.getLanguageName(),
			    factory.getLanguageVersion(), factory.getNames(),
			    factory.getExtensions()));
	    final ScriptEngine engine = factory.getScriptEngine();
	    engine.put("Yamcs", new ScriptHelper());
	    for (String name : factory.getNames()) {
		enginesByName.put(name, engine);
	    }
	    for (String ext : factory.getExtensions()) {
		enginesByExtension.put(ext, engine);
	    }
	}

	// Load algorithms from XML files in classpath
	try {
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    DocumentBuilder db = dbf.newDocumentBuilder();
	    String[] cp = ((String) System.getProperties().get(
		    "java.class.path")).split((String) System.getProperties()
		    .get("path.separator"));
	    for (String dirname : cp) {
		File dir = new File(dirname);
		if (dir.isDirectory() && dir.exists()) {
		    currentXtceDir = new File(dir, "xtce");
		    if (currentXtceDir.isDirectory() && currentXtceDir.exists()) {
			for (File f : currentXtceDir
				.listFiles(new FilenameFilter() {
				    @Override
				    public boolean accept(File f, String name) {
					return name.toLowerCase().endsWith(
						".xml");
				    }
				})) {
			    Element root = db.parse(f).getDocumentElement();
			    if (root.getTagName().equalsIgnoreCase(
				    "SpaceSystem")) {
				loadTag_SpaceSystem(root);
			    } else {
				log.warn(String.format(
					"Cannot find tag SpaceSystem in %s",
					f.getName()));
			    }
			}
		    }
		}
	    }
	} catch (IOException e) {
	    System.err.println(e.getMessage());
	} catch (org.xml.sax.SAXException e) {
	    System.err.println(e.getMessage());
	} catch (ParserConfigurationException e) {
	    System.err.println(e.getMessage());
	}
    }

    @Override
    public Collection<DerivedValue> getDerivedValues() {
	return derivedValues;
    }

    void loadTag_SpaceSystem(Element el) {
	ssName = el.getAttribute("name");
	algoCount = 0;
	NodeList nl = el.getElementsByTagName("TelemetryMetaData");
	for (int i = 0; i < nl.getLength(); ++i) {
	    loadTag_TelemetryMetaData((Element) nl.item(i));
	}
	log.debug(String.format("Loaded %d algorithms from \"%s\"", algoCount,
		ssName));
    }

    void loadTag_TelemetryMetaData(Element el) {
	NodeList nl = el.getElementsByTagName("AlgorithmSet");
	for (int i = 0; i < nl.getLength(); ++i) {
	    loadTag_AlgorithmSet((Element) nl.item(i));
	}
    }

    void loadTag_AlgorithmSet(Element el) {
	NodeList nl = el.getElementsByTagName("CustomAlgorithm");
	for (int i = 0; i < nl.getLength(); ++i) {
	    loadTag_CustomAlgorithm((Element) nl.item(i));
	}
    }

    void loadTag_CustomAlgorithm(Element el) {
	currentCode = null;
	currentInputParams = null;
	currentOutputParam = null;
	currentAlgo = el.getAttribute("name");

	int i;
	NodeList nl = el.getElementsByTagName("AlgorithmText");
	for (i = 0; i < nl.getLength(); ++i) {
	    loadTag_AlgorithmText((Element) nl.item(i));
	}
	nl = el.getElementsByTagName("ExternalAlgorithmSet");
	for (i = 0; i < nl.getLength(); ++i) {
	    loadTag_ExternalAlgorithmSet((Element) nl.item(i));
	}
	nl = el.getElementsByTagName("InputSet");
	for (i = 0; i < nl.getLength(); ++i) {
	    loadTag_InputSet((Element) nl.item(i));
	}
	nl = el.getElementsByTagName("OutputSet");
	for (i = 0; i < nl.getLength(); ++i) {
	    loadTag_OutputSet((Element) nl.item(i));
	}

	if (currentCode == null) {
	    log.warn(String.format("Algorithm %s: no code", currentAlgo));
	} else {
	    Algorithm a = new Algorithm(currentEngine, currentCode,
		    currentInputParams, currentOutputParam);
	    a.def.setQualifiedName("/" + ssName + "/" + currentOutputParam);
	    derivedValues.add(a);
	    ++algoCount;
	}
    }

    void loadTag_AlgorithmText(Element el) {
	// utilise only the first tag
	if (currentCode == null) {
	    final String lang = el.getAttribute("language");
	    currentEngine = enginesByName.get(lang);
	    if (currentEngine != null) {
		NodeList nl = el.getChildNodes();
		if (nl.getLength() > 0) {
		    Text content = (Text) nl.item(0);
		    currentCode = content.getWholeText();
		}
	    } else {
		log.warn(String.format(
			"Algorithm %s: unsupported language \"%s\"",
			currentAlgo, lang));
	    }
	}
    }

    void loadTag_ExternalAlgorithmSet(Element el) {
	NodeList nl = el.getElementsByTagName("ExternalAlgorithm");
	for (int i = 0; i < nl.getLength(); ++i) {
	    loadTag_ExternalAlgorithm((Element) nl.item(i));
	}
    }

    void loadTag_ExternalAlgorithm(Element el) {
	if (currentCode == null) {
	    String loc = el.getAttribute("algorithmLocation");
	    Pattern p = Pattern.compile(".*\\.([^.]+)");
	    Matcher m = p.matcher(loc);
	    if (m.matches()) {
		currentEngine = enginesByExtension.get(m.group(1));
		if (currentEngine != null) {
		    File f = new File(currentXtceDir, loc);
		    try {
			FileInputStream reader = new FileInputStream(f);
			long len = f.length();
			byte[] buf = new byte[(int) len];
			if (reader.read(buf) == len) {
			    currentCode = new String(buf, "ISO-8859-1");
			}
			reader.close();
		    } catch (IOException e) {
			log.warn("Cannot load external algorithm file: "
				+ f.getPath());
		    }
		} else {
		    log.warn(String
			    .format("Algorithm file name %s does not match any known scripting engine extension",
				    loc));
		}
	    } else {
		log.warn(String.format(
			"Algorithm file name %s does not have an extension",
			loc));
	    }
	}
    }

    void loadTag_InputSet(Element el) {
	NodeList nl = el.getElementsByTagName("ParameterInstanceRef");
	currentInputParams = new String[nl.getLength()];
	for (int i = 0; i < nl.getLength(); ++i) {
	    currentInputParams[i] = ((Element) nl.item(i))
		    .getAttribute("parameterName");
	}
    }

    void loadTag_OutputSet(Element el) {
	NodeList nl = el.getElementsByTagName("OutputParameterRef");
	// only one element expected
	for (int i = 0; i < nl.getLength(); ++i) {
	    currentOutputParam = ((Element) nl.item(i))
		    .getAttribute("parameterName");
	}
    }

    Parameter[] getParameters(String[] opsnames) {
	Parameter[] params = new Parameter[opsnames.length];
	for (int i = 0; i < params.length; i++) {
	    params[i] = xtcedb.getParameter(MdbMappings.MDB_OPSNAME,
		    opsnames[i]);
	}
	return params;
    }

    class Algorithm extends DerivedValue {
	String programCode;
	ScriptEngine engine;

	Algorithm(ScriptEngine engine, String programCode, String[] inputParams, String outputParam) {
	    super(outputParam, getParameters(inputParams));
	    this.engine = engine;
	    this.programCode = programCode;
	}

	@Override
	public void updateValue() {
	    for (int i = 0; i < args.length; ++i) {
		final String pname = args[i].getParameter().getName();
		Value v = args[i].getEngValue();

		// Now, update the engine
		switch (v.getType()) {
		case BINARY:
		    engine.put(pname, v.getBinaryValue().toByteArray());
		    break;
		case DOUBLE:
		    engine.put(pname, v.getDoubleValue());
		    break;
		case FLOAT:
		    engine.put(pname, v.getFloatValue());
		    break;
		case SINT32:
		    engine.put(pname, v.getSint32Value());
		    break;
		case STRING:
		    engine.put(pname, v.getStringValue());
		    break;
		case UINT32:
		    engine.put(pname, v.getUint32Value() & 0xFFFFFFFFL);
		    break;
		case SINT64:
		    engine.put(pname, v.getSint64Value());
		    break;
		case UINT64:
		    engine.put(pname, v.getUint64Value() & 0xFFFFFFFFFFFFFFFFL);
		    break;
		case BOOLEAN:
		    engine.put(pname, v.getBooleanValue());
		default:
		    log.warn("Ignoring update of unexpected value type {}",
			    v.getType());
		}
	    }
	    updated = true;
	    engine.put("updated", updated);

	    try {
		engine.eval(programCode);
		updated = (Boolean) engine.get("updated");
		if (updated) {
		    Object res = engine.get(getParameter().getName());
		    if (res == null) {
			log.warn("script variable " + getParameter().getName()
				+ " was not set");
			updated = false;
		    } else {
			if (res instanceof Double) {
			    Double dres = (Double) res;
			    if (dres.longValue() == dres.doubleValue()) {
				setUnsignedIntegerValue(dres.intValue());
			    } else {
				setDoubleValue(dres.doubleValue());
			    }
			} else if (res instanceof Boolean) {
			    setBinaryValue((((Boolean) res).booleanValue() ? "YES"
				    : "NO").getBytes());
			} else {
			    setBinaryValue(res.toString().getBytes());
			}
		    }
		}
	    } catch (ScriptException e) {
		log.warn("script error for " + getParameter().getName() + ": "
			+ e.getMessage());
		updated = false;
	    }
	}
    }

    public class ScriptHelper {
	public Object calibrate(int raw, String parameter) {
	    // calibrate raw value according to the calibration rule of the
	    // given parameter
	    // returns a Float or String object
	    Parameter p = xtcedb.getParameter(parameter);
	    if (p != null) {
		if (p.getParameterType() instanceof EnumeratedParameterType) {
		    EnumeratedParameterType ptype = (EnumeratedParameterType) p
			    .getParameterType();
		    return ptype.calibrate(raw);
		} else {
		    DataEncoding encoding = ((BaseDataType) p
			    .getParameterType()).getEncoding();
		    if (encoding instanceof IntegerDataEncoding) {
			return ((IntegerDataEncoding) encoding)
				.getDefaultCalibrator().calibrate(raw);
		    }
		}
	    } else {
		log.warn(String.format(
			"Cannot find parameter %s to calibrate %d", parameter,
			raw));
	    }
	    return null;
	}

	public long letohl(int value) {
	    // little endian to host long
	    return ((value >> 24) & 0xff) + ((value >> 8) & 0xff00)
		    + ((value & 0xff00) << 8) + ((value & 0xff) << 24);
	}
    }
}
