package org.yamcs;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceMetadata {

    private static final String LABELS = "labels";
    private static final String TEMPLATE = "template";
    private static final String TEMPLATE_SOURCE = "templateSource";
    private static final String TEMPLATE_ARGS = "templateArgs";

    private static final List<String> RESERVED = Arrays.asList(LABELS, TEMPLATE, TEMPLATE_SOURCE, TEMPLATE_ARGS);

    private final Map<String, Object> map;

    public InstanceMetadata() {
        map = new HashMap<>();
    }

    public InstanceMetadata(Map<String, Object> map) {
        this.map = new HashMap<>(map);
    }

    public String getLabel(String label) {
        Map<String, String> labels = getLabels();
        return labels.get(label);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getLabels() {
        if (map.containsKey(LABELS)) {
            return (Map<String, String>) map.get(LABELS);
        }
        return Collections.emptyMap();
    }

    public void setLabels(Map<String, String> labels) {
        map.put(LABELS, labels);
    }

    @SuppressWarnings("unchecked")
    public void putLabel(String label, String value) {
        Map<String, String> labels = (Map<String, String>) map.get(LABELS);
        if (labels == null) {
            labels = new HashMap<>();
            map.put(LABELS, labels);
        }
        labels.put(label, value);
    }

    public String getTemplate() {
        return (String) map.get(TEMPLATE);
    }

    public void setTemplate(String template) {
        map.put(TEMPLATE, template);
    }

    public String getTemplateSource() {
        return (String) map.get(TEMPLATE_SOURCE);
    }

    public void setTemplateSource(String templateSource) {
        map.put(TEMPLATE_SOURCE, templateSource);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTemplateArgs() {
        if (map.containsKey(TEMPLATE_ARGS)) {
            return (Map<String, Object>) map.get(TEMPLATE_ARGS);
        }
        return Collections.emptyMap();
    }

    public void setTemplateArgs(Map<String, Object> templateArgs) {
        map.put(TEMPLATE_ARGS, templateArgs);
    }

    public Object get(Object key) {
        return map.get(key);
    }

    public Object getOrDefault(Object key, Object defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    public Object put(String key, Object value) {
        if (RESERVED.contains(key)) {
            throw new IllegalArgumentException(String.format(
                    "'%s' is a reserved key", key));
        }
        return map.put(key, value);
    }

    /**
     * Returns an unmodifiable map view of this metadata.
     */
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
