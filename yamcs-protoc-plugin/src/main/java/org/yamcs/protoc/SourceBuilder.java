package org.yamcs.protoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceBuilder {

    private String package_;
    private Set<String> imports = new HashSet<>();
    private List<String> annotations = new ArrayList<>();
    private boolean abstract_;
    private String javadoc;
    private String class_;
    private String implements_;
    private List<MethodBuilder> methods = new ArrayList<>();

    public SourceBuilder(String class_) {
        this.class_ = class_;
    }

    public void setJavadoc(String javadoc) {
        this.javadoc = javadoc;
    }

    public void addAnnotation(String annotation) {
        annotations.add(annotation);
    }

    public void setPackage(String package_) {
        this.package_ = package_;
    }

    public void setAbstract(boolean abstract_) {
        this.abstract_ = abstract_;
    }

    public void setImplements(String implements_) {
        this.implements_ = implements_;
    }

    public void addImport(String import_) {
        imports.add(import_);
    }

    public MethodBuilder addMethod(String name) {
        MethodBuilder method = new MethodBuilder(name);
        methods.add(method);
        return method;
    }

    public static class MethodBuilder {

        private String return_ = "void";
        private String name;
        private boolean abstract_;
        private boolean final_;
        private String javadoc;
        private List<String> argTypes = new ArrayList<>();
        private List<String> argNames = new ArrayList<>();
        private List<String> annotations = new ArrayList<>();
        private StringBuilder body = new StringBuilder();

        public MethodBuilder(String name) {
            this.name = name;
        }

        public void setReturn(String return_) {
            this.return_ = return_;
        }

        public void setJavadoc(String javadoc) {
            this.javadoc = javadoc;
        }

        public void setAbstract(boolean abstract_) {
            this.abstract_ = abstract_;
        }

        public void setFinal(boolean final_) {
            this.final_ = final_;
        }

        public void addArg(String type, String name) {
            argTypes.add(type);
            argNames.add(name);
        }

        public void addAnnotation(String annotation) {
            annotations.add(annotation);
        }

        public StringBuilder body() {
            return body;
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("package ").append(package_).append(";\n\n");

        List<String> sortedImports = new ArrayList<>(imports);
        Collections.sort(sortedImports);
        for (String import_ : sortedImports) {
            if (!import_.equals(package_)) {
                buf.append("import ").append(import_).append(";\n");
            }
        }
        buf.append("\n");

        if (javadoc != null) {
            buf.append("/**\n");
            buf.append(" * ").append(javadoc.trim()).append("\n");
            buf.append(" */\n");
        }

        for (String annotation : annotations) {
            buf.append(annotation).append("\n");
        }

        String modifiers = "public";
        if (abstract_) {
            modifiers += " abstract";
        }

        buf.append(modifiers).append(" class ").append(class_);
        if (implements_ != null) {
            buf.append(" implements ").append(implements_);
        }
        buf.append(" {\n");

        for (MethodBuilder method : methods) {
            buf.append("\n");
            if (method.javadoc != null) {
                buf.append("    /**\n");
                buf.append("     * ").append(method.javadoc.trim()).append("\n");
                buf.append("     */\n");
            }
            for (String annotation : method.annotations) {
                buf.append("    ").append(annotation).append("\n");
            }
            modifiers = "public";
            if (method.abstract_) {
                modifiers += " abstract";
            }
            if (method.final_) {
                modifiers += " final";
            }
            if (method.abstract_) {
                buf.append("    ").append(modifiers).append(" ").append(method.return_).append(" ").append(method.name);
                buf.append("(");
                for (int i = 0; i < method.argTypes.size(); i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    buf.append(method.argTypes.get(i)).append(" ").append(method.argNames.get(i));
                }
                buf.append(");\n");
            } else {
                buf.append("    ").append(modifiers).append(" ").append(method.return_).append(" ").append(method.name);
                buf.append("(");
                for (int i = 0; i < method.argTypes.size(); i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    buf.append(method.argTypes.get(i)).append(" ").append(method.argNames.get(i));
                }
                buf.append(") {\n");
                String[] lines = method.body.toString().trim().split("\n");
                for (int i = 0; i < lines.length; i++) {
                    buf.append("        ").append(lines[i]).append("\n");
                }
                buf.append("    }\n");
            }
        }

        return buf.append("}\n").toString();
    }
}
