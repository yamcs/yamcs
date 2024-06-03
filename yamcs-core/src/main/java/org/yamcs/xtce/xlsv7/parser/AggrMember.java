package org.yamcs.xtce.xlsv7.parser;

public class AggrMember {
        final String name;
        final String dataType;
        final String description;

        AggrMember(String name, String dataType, String description) {
            this.name = name;
            this.dataType = dataType;
            this.description = description;
        }

        public String name() {
            return name;
        }
        public String dataType() {
            return dataType;
        }
        public String description() {
            return description;
        }
    }
