package com.example.dataflow.generic.parser.config;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ParserRegistryConfig implements Serializable {
    private List<ParserConfig> parsers;

    public List<ParserConfig> getParsers() {
        return parsers;
    }

    public void setParsers(List<ParserConfig> parsers) {
        this.parsers = parsers;
    }

    public static class ParserConfig implements Serializable {
        private String format;
        private String className;
        private Map<String, Object> config;

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }
}
