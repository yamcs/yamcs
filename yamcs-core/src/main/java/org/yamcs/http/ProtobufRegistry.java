package org.yamcs.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.yamcs.api.AnnotationsProto;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.WebSocketTopic;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;

public class ProtobufRegistry {

    // Indexes by fully-qualified protobuf name
    private Map<String, RpcDescriptor> rpcs = new HashMap<>();
    private Map<String, DescriptorProto> messageTypes = new HashMap<>();
    private Map<String, EnumDescriptorProto> enumTypes = new HashMap<>();
    private Map<String, String> javaPackages = new HashMap<>();

    // By symbol (e.g. package.service.method or package.message.field)
    private Map<String, String> leadingComments = new HashMap<>();

    private ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    private Map<Descriptor, List<ExtensionInfo>> extensionsByMessage = new HashMap<>();

    public ProtobufRegistry() {
        extensionRegistry.add(AnnotationsProto.route);
        extensionRegistry.add(AnnotationsProto.websocket);

        try (InputStream in = getClass().getResourceAsStream("/yamcs-api.protobin")) {
            if (in == null) {
                throw new UnsupportedOperationException("Missing binary protobuf descriptions");
            }
            importDefinitions(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void importDefinitions(InputStream in) throws IOException {
        if (in == null) {
            throw new NullPointerException("input stream cannot be null");
        }
        FileDescriptorSet proto = FileDescriptorSet.parseFrom(in, extensionRegistry);

        // Index all messages by fully-qualified protobuf name
        for (FileDescriptorProto file : proto.getFileList()) {
            extractComments(file);
            String javaPackage = file.getOptions().getJavaPackage();
            javaPackages.put(file.getName(), javaPackage);

            for (DescriptorProto messageType : file.getMessageTypeList()) {
                String qname = "." + file.getPackage() + "." + messageType.getName();
                messageTypes.put(qname, messageType);

                for (EnumDescriptorProto enumType : messageType.getEnumTypeList()) {
                    String enumQname = qname + "." + enumType.getName();
                    enumTypes.put(enumQname, enumType);
                }
                for (DescriptorProto nestedType : messageType.getNestedTypeList()) {
                    String nestedQname = qname + "." + nestedType.getName();
                    messageTypes.put(nestedQname, nestedType);
                }
            }

            for (EnumDescriptorProto enumType : file.getEnumTypeList()) {
                String qname = "." + file.getPackage() + "." + enumType.getName();
                enumTypes.put(qname, enumType);
            }
        }

        // Index RPCs
        for (FileDescriptorProto file : proto.getFileList()) {
            var packageName = file.getPackage();
            for (ServiceDescriptorProto service : file.getServiceList()) {
                for (MethodDescriptorProto method : service.getMethodList()) {
                    MethodOptions options = method.getOptions();
                    String serviceName = service.getName();
                    String methodName = method.getName();
                    DescriptorProto inputType = messageTypes.get(method.getInputType());
                    DescriptorProto outputType = messageTypes.get(method.getOutputType());
                    if (options.hasExtension(AnnotationsProto.route)) {
                        HttpRoute route = options.getExtension(AnnotationsProto.route);
                        RpcDescriptor descriptor = new RpcDescriptor(packageName, serviceName, methodName,
                                method.getInputType(), inputType,
                                method.getOutputType(), outputType,
                                route);

                        String qname = String.join(".", packageName, serviceName, methodName);
                        rpcs.put(qname, descriptor);
                    } else if (options.hasExtension(AnnotationsProto.websocket)) {
                        WebSocketTopic topic = options.getExtension(AnnotationsProto.websocket);
                        RpcDescriptor descriptor = new RpcDescriptor(packageName, serviceName, methodName,
                                method.getInputType(), inputType,
                                method.getOutputType(), outputType,
                                topic);

                        String qname = String.join(".", packageName, serviceName, methodName);
                        rpcs.put(qname, descriptor);
                    }
                }
            }
        }
    }

    private void extractComments(FileDescriptorProto file) {
        for (var location : file.getSourceCodeInfo().getLocationList()) {
            if (location.hasLeadingComments()) {
                var symbol = makeSymbol(file, location.getPathList());
                var comments = location.getLeadingComments().stripTrailing();
                if (comments.split("\n").length == 1) {
                    leadingComments.put(symbol, comments.stripLeading());
                } else {
                    leadingComments.put(symbol, comments);
                }
            }
        }
    }

    private static String makeSymbol(FileDescriptorProto file, List<Integer> path) {
        var it = path.iterator();
        var symbol = "." + file.getPackage();

        if (it.hasNext()) {
            var item = it.next();
            if (item == FileDescriptorProto.PACKAGE_FIELD_NUMBER) {
                // Ignore
            } else if (item == FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER) {
                var idx = it.next();
                var messageType = file.getMessageType(idx);
                var messageSymbol = symbol + "." + makeSymbol(it, messageType);
                return messageSymbol;
            } else if (item == FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER) {
                var idx = it.next();
                var enumType = file.getEnumType(idx);
                var enumSymbol = symbol + "." + makeSymbol(it, enumType);
                return enumSymbol;
            } else if (item == FileDescriptorProto.SERVICE_FIELD_NUMBER) {
                var idx = it.next();
                var service = file.getService(idx);
                var serviceSymbol = symbol + "." + makeSymbol(it, service);
                return serviceSymbol;
            } else if (item == FileDescriptorProto.EXTENSION_FIELD_NUMBER) {
                var idx = it.next();
                var extension = file.getExtension(idx);
                var extensionSymbol = symbol + "." + extension.getName();
                return extensionSymbol;
            } else if (item == FileDescriptorProto.OPTIONS_FIELD_NUMBER) {
                // Ignore
                it.next();
            } else if (item == FileDescriptorProto.SOURCE_CODE_INFO_FIELD_NUMBER) {
                // Ignore
            } else if (item == FileDescriptorProto.SYNTAX_FIELD_NUMBER) {
                // Ignore
            } else {
                throw new IllegalArgumentException("Unexpected item " + item);
            }
        }

        if (it.hasNext()) {
            var item = it.next();
            throw new IllegalArgumentException("Unexpected item " + item);
        }

        return symbol;
    }

    private static String makeSymbol(Iterator<Integer> it, DescriptorProto messageType) {
        var symbol = messageType.getName();
        if (it.hasNext()) {
            var item = it.next();
            if (item == 1) { // Name
                // Ignore
            } else if (item == DescriptorProto.FIELD_FIELD_NUMBER) {
                var idx = it.next();
                var field = messageType.getField(idx);
                return symbol + "." + makeSymbol(it, field);
            } else if (item == DescriptorProto.NESTED_TYPE_FIELD_NUMBER) {
                var idx = it.next();
                var nestedType = messageType.getNestedType(idx);
                return symbol + "." + makeSymbol(it, nestedType);
            } else if (item == DescriptorProto.ENUM_TYPE_FIELD_NUMBER) {
                var idx = it.next();
                var enumType = messageType.getEnumType(idx);
                return symbol + "." + makeSymbol(it, enumType);
            } else if (item == DescriptorProto.EXTENSION_RANGE_FIELD_NUMBER) {
                // Ignore
            } else if (item == DescriptorProto.ONEOF_DECL_FIELD_NUMBER) {
                var idx = it.next();
                var oneof = messageType.getOneofDecl(idx);
                return symbol + "." + oneof.getName();
            } else {
                throw new IllegalArgumentException("Unexpected item " + item);
            }
        }

        return symbol;
    }

    private static String makeSymbol(Iterator<Integer> it, EnumDescriptorProto enumProto) {
        var symbol = enumProto.getName();
        if (it.hasNext()) {
            var item = it.next();
            if (item == EnumDescriptorProto.VALUE_FIELD_NUMBER) {
                var idx = it.next();
                var value = enumProto.getValue(idx);
                return symbol + "." + value.getName();
            } else {
                throw new IllegalArgumentException("Unexpected item " + item);
            }
        }

        if (it.hasNext()) {
            var item = it.next();
            throw new IllegalArgumentException("Unexpected item " + item);
        }

        return symbol;
    }

    private static String makeSymbol(Iterator<Integer> it, ServiceDescriptorProto service) {
        var symbol = service.getName();
        if (it.hasNext()) {
            var item = it.next();
            if (item == ServiceDescriptorProto.METHOD_FIELD_NUMBER) {
                var idx = it.next();
                var method = service.getMethod(idx);
                return symbol + "." + method.getName();
            } else {
                throw new IllegalArgumentException("Unexpected item " + item);
            }
        }

        if (it.hasNext()) {
            var item = it.next();
            throw new IllegalArgumentException("Unexpected item " + item);
        }

        return symbol;
    }

    private static String makeSymbol(Iterator<Integer> it, FieldDescriptorProto field) {
        if (it.hasNext()) {
            var item = it.next();
            throw new IllegalArgumentException("Unexpected item " + item);
        }
        return field.getName();
    }

    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    public RpcDescriptor getRpc(String id) {
        return rpcs.get(id);
    }

    /**
     * Find the comment associate to a symbol
     */
    public String getComment(String symbol) {
        return leadingComments.get(symbol);
    }

    public DescriptorProto getMessageType(String symbol) {
        return messageTypes.get(symbol);
    }

    public EnumDescriptorProto getEnumType(String symbol) {
        return enumTypes.get(symbol);
    }

    /**
     * Returns extensions for a specific Message type in the order as they have been defined
     */
    public List<ExtensionInfo> getExtensions(Descriptor messageType) {
        List<ExtensionInfo> extensions = extensionsByMessage.get(messageType);
        return (extensions == null) ? Collections.emptyList() : extensions;
    }
}
