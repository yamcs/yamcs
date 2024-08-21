package org.yamcs.pus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.parameter.DoubleValue;
import org.yamcs.parameter.FloatValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.parameter.SInt32Value;
import org.yamcs.parameter.SInt64Value;
import org.yamcs.parameter.UInt32Value;
import org.yamcs.parameter.UInt64Value;
import org.yamcs.parameter.Value;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

/**
 * Creates messages (strings) from templates by substituting parameter values
 */
public class MessageTemplate {
    final static Log log = new Log(MessageTemplate.class);

    final List<TemplatePart> parts;

    MessageTemplate(String template) {
        this(template, null);
    }

    MessageTemplate(String template, Mdb mdb) {
        this.parts = new ArrayList<>();
        int start = 0;

        while (start < template.length()) {
            int openBrace = template.indexOf('{', start);
            if (openBrace == -1) {
                // Add the remaining text as a TextTemplatePart
                parts.add(new TextTemplatePart(template.substring(start)));
                break;
            }

            // Add any text before the open brace as a TextTemplatePart
            if (openBrace > start) {
                parts.add(new TextTemplatePart(template.substring(start, openBrace)));
            }

            int closeBrace = template.indexOf('}', openBrace);
            if (closeBrace == -1) {
                // Unmatched '{' found; treat it as plain text
                parts.add(new TextTemplatePart(template.substring(openBrace)));
                break;
            }

            // Extract the placeholder content between '{' and '}'
            String placeholder = template.substring(openBrace + 1, closeBrace);
            parts.add(new ParameterTemplatePart(placeholder, mdb));

            // Move the start position past the closing brace for the next iteration
            start = closeBrace + 1;
        }

    }

    interface TemplatePart {
        String format(ParameterValueResolver resolver);
    }

    record TextTemplatePart(String text) implements TemplatePart {
        @Override
        public String format(ParameterValueResolver resolver) {
            return text;
        }
    }

    class ParameterTemplatePart implements TemplatePart {
        Parameter para;
        String paraName;
        boolean raw;
        PathElement[] path;
        String format;

        ParameterTemplatePart(String s, Mdb mdb) {
            String[] a = s.split(";");
            if (a.length > 1) {
                this.format = a[1].trim();
                s = a[0].trim();
            }

            if (s.endsWith(".raw")) {
                this.raw = true;
                s = s.substring(0, s.length() - 4);
            }
            var idx = s.indexOf('.');
            if (idx == -1) {
                this.paraName = s;
            } else {
                this.paraName = s.substring(0, idx);
                this.path = AggregateUtil.parseReference(s.substring(idx + 1));
            }

            if (mdb != null && this.paraName.startsWith("/")) {
                this.para = mdb.getParameter(this.paraName);
                if (this.para == null) {
                    throw new ConfigurationException("Cannot find parameter " + this.paraName);
                }
            }
        }

        @Override
        public String format(ParameterValueResolver resolver) {
            RawEngValue pv = null;
            if (para != null) {
                pv = resolver.resolve(para);
            } else {
                pv = resolver.resolve(paraName);
            }
            if (pv == null) {
                return null;
            }
            Value v = raw ? pv.getRawValue() : pv.getEngValue();
            if (path != null) {
                v = AggregateUtil.getMemberValue(v, path);
            }
            if (v == null) {
                return null;
            }
            if (format != null) {
                try {
                    return format(v);
                } catch (IllegalFormatException e) {
                    log.warn("Invalid format {} for parameter {}: {}", format, paraName, e.getMessage());
                    return v.toString();
                }
            } else {
                return v.toString();
            }
        }

        @Override
        public String toString() {
            return "ParameterTemplatePart [para=" + para + ", paraName=" + paraName + ", raw=" + raw + ", path="
                    + Arrays.toString(path) + ", format=" + format + "]";
        }

        private String format(Value v) {
            if (v instanceof FloatValue v1) {
                return String.format(format, v1.getFloatValue());
            } else if (v instanceof DoubleValue v1) {
                return String.format(format, v1.getDoubleValue());
            } else if (v instanceof SInt32Value v1) {
                return String.format(format, v1.getSint32Value());
            } else if (v instanceof SInt64Value v1) {
                return String.format(format, v1.getSint64Value());
            } else if (v instanceof UInt32Value v1) {
                return String.format(format, v1.getUint32Value());
            } else if (v instanceof UInt64Value v1) {
                return String.format(format, v1.getUint64Value());
            } else {
                return v.toString();
            }
        }
    }

    public String format(ParameterValueResolver resolver) {
        StringBuilder result = new StringBuilder();
        for (var tp : parts) {
            var s = tp.format(resolver);
            if (s != null) {
                result.append(s);
            }
        }
        return result.toString();
    }

    /**
     * The template can use either parameter absolute qualified names {/a/b/c/parameterName} or just {name}
     * <p>
     * When replacing patterns in the template the resolver has to be able to look up one of the two
     */
    interface ParameterValueResolver {
        /**
         * return the value for the parameter p.
         * <p>
         * may return null
         */
        RawEngValue resolve(Parameter p);

        /**
         * return the value for the given name.
         * <p>
         * may return null
         */
        RawEngValue resolve(String name);
    }
}
