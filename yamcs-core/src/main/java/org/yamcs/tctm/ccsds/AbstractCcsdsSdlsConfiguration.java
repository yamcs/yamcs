package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

abstract class AbstractCcsdsSdlsConfiguration implements CcsdsFrameSecurityConfiguration {
    private YConfiguration securityConfig;

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration args) {
        securityConfig = args;
    }

    @Override
    public YConfiguration applyTo(YConfiguration protocolConfiguration) {
        Map<String, Object> result = copyMap(protocolConfiguration.toMap());
        if (securityConfig.containsKey("encryption")) {
            result.put("encryption", copyValue(securityConfig.toMap().get("encryption")));
        }

        if (securityConfig.containsKey("virtualChannels")) {
            Object configuredVcs = result.get("virtualChannels");
            if (!(configuredVcs instanceof List<?>)) {
                throw new ConfigurationException("CCSDS protocol configuration has no virtualChannels list");
            }
            @SuppressWarnings("unchecked")
            List<Object> vcs = (List<Object>) configuredVcs;
            Map<Integer, Map<String, Object>> byVcId = new LinkedHashMap<>();
            for (Object value : vcs) {
                if (!(value instanceof Map<?, ?>)) {
                    throw new ConfigurationException("CCSDS virtual channel configuration must be a map");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> vc = (Map<String, Object>) value;
                byVcId.put(YConfiguration.getInt(vc, "vcId"), vc);
            }

            for (YConfiguration assignment : securityConfig.getConfigList("virtualChannels")) {
                int vcId = assignment.getInt("vcId");
                Map<String, Object> target = byVcId.get(vcId);
                if (target == null) {
                    throw new ConfigurationException("SDLS references unknown CCSDS vcId " + vcId);
                }
                String selectionKey = isTelecommand() ? "encryptionSpi" : "encryptionSpis";
                if (!assignment.containsKey(selectionKey)) {
                    throw new ConfigurationException("SDLS vcId " + vcId + " is missing " + selectionKey);
                }
                target.put(selectionKey, copyValue(assignment.toMap().get(selectionKey)));
            }
        }
        return YConfiguration.wrap(result);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copyValue(value)));
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> result.put(String.valueOf(key), copyValue(nested)));
            return result;
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(nested -> result.add(copyValue(nested)));
            return result;
        }
        return value;
    }
}
