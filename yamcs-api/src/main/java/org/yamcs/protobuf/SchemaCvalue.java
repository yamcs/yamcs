// Generated by http://code.google.com/p/protostuff/ ... DO NOT EDIT!
// Generated from cvalue.proto

package org.yamcs.protobuf;


public final class SchemaCvalue
{

    public static final class ContainerValue
    {
        public static final org.yamcs.protobuf.SchemaCvalue.ContainerValue.MessageSchema WRITE =
            new org.yamcs.protobuf.SchemaCvalue.ContainerValue.MessageSchema();
        public static final org.yamcs.protobuf.SchemaCvalue.ContainerValue.BuilderSchema MERGE =
            new org.yamcs.protobuf.SchemaCvalue.ContainerValue.BuilderSchema();
        
        public static class MessageSchema implements io.protostuff.Schema<org.yamcs.protobuf.Cvalue.ContainerValue>
        {
            public void writeTo(io.protostuff.Output output, org.yamcs.protobuf.Cvalue.ContainerValue message) throws java.io.IOException
            {
                if(message.hasId())
                    output.writeObject(1, message.getId(), org.yamcs.protobuf.SchemaYamcs.NamedObjectId.WRITE, false);

                for(org.yamcs.protobuf.Pvalue.ParameterValue parameter : message.getParameterList())
                    output.writeObject(2, parameter, org.yamcs.protobuf.SchemaPvalue.ParameterValue.WRITE, true);

            }
            public boolean isInitialized(org.yamcs.protobuf.Cvalue.ContainerValue message)
            {
                return message.isInitialized();
            }
            public java.lang.String getFieldName(int number)
            {
                return org.yamcs.protobuf.SchemaCvalue.ContainerValue.getFieldName(number);
            }
            public int getFieldNumber(java.lang.String name)
            {
                return org.yamcs.protobuf.SchemaCvalue.ContainerValue.getFieldNumber(name);
            }
            public java.lang.Class<org.yamcs.protobuf.Cvalue.ContainerValue> typeClass()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.class;
            }
            public java.lang.String messageName()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.class.getSimpleName();
            }
            public java.lang.String messageFullName()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.class.getName();
            }
            //unused
            public void mergeFrom(io.protostuff.Input input, org.yamcs.protobuf.Cvalue.ContainerValue message) throws java.io.IOException {}
            public org.yamcs.protobuf.Cvalue.ContainerValue newMessage() { return null; }
        }
        public static class BuilderSchema implements io.protostuff.Schema<org.yamcs.protobuf.Cvalue.ContainerValue.Builder>
        {
            public void mergeFrom(io.protostuff.Input input, org.yamcs.protobuf.Cvalue.ContainerValue.Builder builder) throws java.io.IOException
            {
                for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
                {
                    switch(number)
                    {
                        case 0:
                            return;
                        case 1:
                            builder.setId(input.mergeObject(org.yamcs.protobuf.Yamcs.NamedObjectId.newBuilder(), org.yamcs.protobuf.SchemaYamcs.NamedObjectId.MERGE));

                            break;
                        case 2:
                            builder.addParameter(input.mergeObject(org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder(), org.yamcs.protobuf.SchemaPvalue.ParameterValue.MERGE));

                            break;
                        default:
                            input.handleUnknownField(number, this);
                    }
                }
            }
            public boolean isInitialized(org.yamcs.protobuf.Cvalue.ContainerValue.Builder builder)
            {
                return builder.isInitialized();
            }
            public org.yamcs.protobuf.Cvalue.ContainerValue.Builder newMessage()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.newBuilder();
            }
            public java.lang.String getFieldName(int number)
            {
                return org.yamcs.protobuf.SchemaCvalue.ContainerValue.getFieldName(number);
            }
            public int getFieldNumber(java.lang.String name)
            {
                return org.yamcs.protobuf.SchemaCvalue.ContainerValue.getFieldNumber(name);
            }
            public java.lang.Class<org.yamcs.protobuf.Cvalue.ContainerValue.Builder> typeClass()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.Builder.class;
            }
            public java.lang.String messageName()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.class.getSimpleName();
            }
            public java.lang.String messageFullName()
            {
                return org.yamcs.protobuf.Cvalue.ContainerValue.class.getName();
            }
            //unused
            public void writeTo(io.protostuff.Output output, org.yamcs.protobuf.Cvalue.ContainerValue.Builder builder) throws java.io.IOException {}
        }
        public static java.lang.String getFieldName(int number)
        {
            switch(number)
            {
                case 1: return "id";
                case 2: return "parameter";
                default: return null;
            }
        }
        public static int getFieldNumber(java.lang.String name)
        {
            java.lang.Integer number = fieldMap.get(name);
            return number == null ? 0 : number.intValue();
        }
        private static final java.util.HashMap<java.lang.String,java.lang.Integer> fieldMap = new java.util.HashMap<java.lang.String,java.lang.Integer>();
        static
        {
            fieldMap.put("id", 1);
            fieldMap.put("parameter", 2);
        }
    }

}
