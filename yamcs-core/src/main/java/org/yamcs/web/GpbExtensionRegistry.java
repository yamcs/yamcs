package org.yamcs.web;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.Event;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;
import com.google.protobuf.InvalidProtocolBufferException;

public class GpbExtensionRegistry {

    private static final Logger log = LoggerFactory.getLogger(GpbExtensionRegistry.class);

    private ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    private Map<Descriptor, List<ExtensionInfo>> extensionsByMessage = new HashMap<>();

    public void installExtension(Class<?> extensionClass, Field field)
            throws IllegalArgumentException, IllegalAccessException {
        @SuppressWarnings("unchecked")
        GeneratedExtension<?, Type> genExtension = (GeneratedExtension<?, Type>) field.get(null);
        extensionRegistry.add(genExtension);

        Descriptor extendedMessage = genExtension.getDescriptor().getContainingType();
        FieldDescriptor extensionField = genExtension.getDescriptor();
        log.info("Installing {} extension: {}", extendedMessage.getFullName(), extensionField.getFullName());

        List<ExtensionInfo> fieldExtensions = extensionsByMessage.get(extendedMessage);
        if (fieldExtensions == null) {
            fieldExtensions = new ArrayList<>();
            extensionsByMessage.put(extendedMessage, fieldExtensions);
        }

        ExtensionInfo extensionInfo = extensionRegistry.findExtensionByName(extensionField.getFullName());
        fieldExtensions.add(extensionInfo);
    }

    public Event getExtendedEvent(Event original) {
        try {
            return Event.parseFrom(original.toByteArray(), extensionRegistry);
        } catch (InvalidProtocolBufferException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Returns extensions for a specific Message type in the order as they have been defined
     */
    public List<ExtensionInfo> getExtensions(Descriptor messageType) {
        List<ExtensionInfo> extensions = extensionsByMessage.get(messageType);
        return (extensions == null) ? Collections.emptyList() : extensions;
    }
}
