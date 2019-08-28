package org.yamcs.protoc;

import java.beans.Introspector;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.protoc.SourceBuilder.MethodBuilder;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;

public class ServiceGenerator {

    private static Map<String, DescriptorProto> messageTypes = new HashMap<>();
    private static Map<DescriptorProto, FileDescriptorProto> fileForMessage = new HashMap<>();
    private static Map<String, String> javaPackages = new HashMap<>();

    private static Map<ServiceDescriptorProto, String> serviceComments = new HashMap<>();
    private static Map<MethodDescriptorProto, String> methodComments = new HashMap<>();

    private static void scanComments(FileDescriptorProto file) {
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

    public static void main(String[] args) throws IOException {
        CodeGeneratorRequest request = CodeGeneratorRequest.parseFrom(System.in);
        CodeGeneratorResponse.Builder responseb = CodeGeneratorResponse.newBuilder();

        // Index all messages by fully-qualified protobuf name
        for (FileDescriptorProto file : request.getProtoFileList()) {
            scanComments(file);

            String javaPackage = file.getOptions().getJavaPackage();
            javaPackages.put(file.getName(), javaPackage);

            for (DescriptorProto messageType : file.getMessageTypeList()) {
                String qname = file.getPackage() + "." + messageType.getName();
                messageTypes.put(qname, messageType);
                fileForMessage.put(messageType, file);
            }
        }

        for (FileDescriptorProto file : request.getProtoFileList()) {
            for (int i = 0; i < file.getServiceCount(); i++) {
                responseb.addFile(generateService(file, i));
            }
        }

        responseb.build().writeTo(System.out);
    }

    private static File.Builder generateService(FileDescriptorProto file, int serviceIndex) {
        ServiceDescriptorProto service = file.getService(serviceIndex);
        String javaPackage = file.getOptions().getJavaPackage();
        String javaName = "Abstract" + service.getName();

        SourceBuilder jsource = new SourceBuilder(javaName + "<T>");
        jsource.setAbstract(true);
        jsource.setJavadoc(serviceComments.get(service));
        jsource.setPackage(javaPackage);
        jsource.setImplements("Api<T>");
        String className = ServiceGenerator.class.getName();
        // Uncomment when dropping java 8 (this annotation is only available as of java 9.
        jsource.addAnnotation("// @javax.annotation.processing.Generated(value = \"" + className + "\", date = \""
                + Instant.now() + "\")");
        jsource.addImport("com.google.protobuf.Message");
        jsource.addImport("com.google.protobuf.Descriptors.MethodDescriptor");
        jsource.addImport("com.google.protobuf.Descriptors.ServiceDescriptor");
        jsource.addImport("org.yamcs.api.Api");
        jsource.addImport("org.yamcs.api.Observer");

        for (MethodDescriptorProto method : service.getMethodList()) {
            String javaMethodName = Introspector.decapitalize(method.getName());
            DescriptorProto inputType = messageTypes.get(method.getInputType().substring(1));
            DescriptorProto outputType = messageTypes.get(method.getOutputType().substring(1));

            String inputTypeJavaPackage = getJavaPackage(inputType);
            if (!inputTypeJavaPackage.equals(javaPackage)) {
                jsource.addImport(getJavaClassname(inputType));
            }

            String outputTypeJavaPackage = getJavaPackage(outputType);
            if (!outputTypeJavaPackage.equals(javaPackage)) {
                jsource.addImport(getJavaClassname(outputType));
            }

            MethodBuilder msource = jsource.addMethod(javaMethodName);
            msource.setJavadoc(methodComments.get(method));
            msource.setAbstract(true);
            msource.addArg("T", "ctx");
            msource.addArg(inputType.getName(), "request");
            msource.addArg("Observer<" + outputType.getName() + ">", "observer");
        }

        // Implement "ServiceDescriptor getDescriptorForType();"
        MethodBuilder msource = jsource.addMethod("getDescriptorForType");
        msource.setReturn("ServiceDescriptor");
        msource.addAnnotation("@Override");
        msource.setFinal(true);
        msource.body().append("return ").append(getOuterClassname(file))
                .append(".getDescriptor().getServices().get(").append(serviceIndex).append(");\n");

        // Implement "Message getRequestPrototype(MethodDescriptor method);"
        msource = jsource.addMethod("getRequestPrototype");
        msource.setReturn("Message");
        msource.addAnnotation("@Override");
        msource.setFinal(true);
        msource.addArg("MethodDescriptor", "method");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            MethodDescriptorProto method = service.getMethod(i);
            DescriptorProto inputType = messageTypes.get(method.getInputType().substring(1));
            msource.body().append("case ").append(i).append(":\n");
            msource.body().append("    return ").append(inputType.getName()).append(".getDefaultInstance();\n");
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        // Implement "Message getResponsePrototype(MethodDescriptor method);"
        msource = jsource.addMethod("getResponsePrototype");
        msource.setReturn("Message");
        msource.setFinal(true);
        msource.addAnnotation("@Override");
        msource.addArg("MethodDescriptor", "method");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            MethodDescriptorProto method = service.getMethod(i);
            DescriptorProto outputType = messageTypes.get(method.getOutputType().substring(1));
            msource.body().append("case ").append(i).append(":\n");
            msource.body().append("    return ").append(outputType.getName()).append(".getDefaultInstance();\n");
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        // Implement "void callMethod(MethodDescriptor method, Message request, Observer<Message> observer)"
        msource = jsource.addMethod("callMethod");
        msource.setFinal(true);
        msource.addAnnotation("@Override");
        msource.addAnnotation("@SuppressWarnings(\"unchecked\")");
        msource.addArg("MethodDescriptor", "method");
        msource.addArg("T", "ctx");
        msource.addArg("Message", "request");
        msource.addArg("Observer<Message>", "future");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            MethodDescriptorProto method = service.getMethod(i);
            String javaMethodName = Introspector.decapitalize(method.getName());
            DescriptorProto inputType = messageTypes.get(method.getInputType().substring(1));
            DescriptorProto outputType = messageTypes.get(method.getOutputType().substring(1));
            String callArgs = "ctx, (" + inputType.getName() + ") request";
            callArgs += ", (Observer<" + outputType.getName() + ">)(Object) future";
            msource.body().append("case ").append(i).append(":\n");
            msource.body().append("    ").append(javaMethodName).append("(").append(callArgs).append(");\n");
            msource.body().append("    return;\n");
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        String filename = javaPackage.replace('.', '/') + "/" + javaName + ".java";
        return File.newBuilder().setName(filename).setContent(jsource.toString());
    }

    private static String getJavaPackage(DescriptorProto messageType) {
        FileDescriptorProto file = fileForMessage.get(messageType);
        if (file.getOptions().getJavaMultipleFiles()) {
            return file.getOptions().getJavaPackage();
        } else {
            String outerClassname = getOuterClassname(file);
            return file.getOptions().getJavaPackage() + "." + outerClassname;
        }
    }

    private static String getOuterClassname(FileDescriptorProto file) {
        if (file.getOptions().hasJavaOuterClassname()) {
            return file.getOptions().getJavaOuterClassname();
        } else {
            String name = new java.io.File(file.getName()).toPath().getFileName().toString().replace(".proto", "");
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    private static String getJavaClassname(DescriptorProto messageType) {
        return getJavaPackage(messageType) + "." + messageType.getName();
    }
}
