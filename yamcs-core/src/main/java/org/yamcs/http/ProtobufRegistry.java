package org.yamcs.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.api.AnnotationsProto;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.WebSocketTopic;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;

public class ProtobufRegistry {

    // Indexes by fully-qualified protobuf name
    private Map<String, RpcDescriptor> rpcs = new HashMap<>();
    private Map<String, DescriptorProto> messageTypes = new HashMap<>();
    private Map<String, String> javaPackages = new HashMap<>();

    private Map<ServiceDescriptorProto, String> serviceComments = new HashMap<>();
    private Map<MethodDescriptorProto, String> methodComments = new HashMap<>();

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
            scanComments(file);
            String javaPackage = file.getOptions().getJavaPackage();
            javaPackages.put(file.getName(), javaPackage);

            for (DescriptorProto messageType : file.getMessageTypeList()) {
                String qname = file.getPackage() + "." + messageType.getName();
                messageTypes.put(qname, messageType);
            }
        }

        // Index RPCs
        for (FileDescriptorProto file : proto.getFileList()) {
            for (ServiceDescriptorProto service : file.getServiceList()) {
                for (MethodDescriptorProto method : service.getMethodList()) {
                    MethodOptions options = method.getOptions();
                    String serviceName = service.getName();
                    String methodName = method.getName();
                    DescriptorProto inputType = messageTypes.get(method.getInputType().substring(1));
                    DescriptorProto outputType = messageTypes.get(method.getOutputType().substring(1));
                    if (options.hasExtension(AnnotationsProto.route)) {
                        HttpRoute route = options.getExtension(AnnotationsProto.route);
                        RpcDescriptor descriptor = new RpcDescriptor(serviceName, methodName, inputType, outputType,
                                route);

                        String qname = String.join(".", file.getPackage(), serviceName, methodName);
                        rpcs.put(qname, descriptor);
                    } else if (options.hasExtension(AnnotationsProto.websocket)) {
                        WebSocketTopic topic = options.getExtension(AnnotationsProto.websocket);
                        RpcDescriptor descriptor = new RpcDescriptor(serviceName, methodName, inputType, outputType,
                                topic);

                        String qname = String.join(".", file.getPackage(), serviceName, methodName);
                        rpcs.put(qname, descriptor);
                    }
                }
            }
        }
    }

    private void scanComments(FileDescriptorProto file) {
        List<ServiceDescriptorProto> services = file.getServiceList();

        for (Location location : file.getSourceCodeInfo().getLocationList()) {
            if (location.hasLeadingComments()) {
                if (location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER) {
                    ServiceDescriptorProto service = services.get(location.getPath(1));
                    if (location.getPathCount() == 2) {
                        serviceComments.put(service, location.getLeadingComments());
                    } else if (location.getPathCount() == 4) {
                        if (location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER) {
                            MethodDescriptorProto method = service.getMethod(location.getPath(3));
                            methodComments.put(method, location.getLeadingComments());
                        }
                    }
                }
            }
        }
    }

    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    public RpcDescriptor getRpc(String id) {
        return rpcs.get(id);
    }

    /**
     * Returns extensions for a specific Message type in the order as they have been defined
     */
    public List<ExtensionInfo> getExtensions(Descriptor messageType) {
        List<ExtensionInfo> extensions = extensionsByMessage.get(messageType);
        return (extensions == null) ? Collections.emptyList() : extensions;
    }
}
