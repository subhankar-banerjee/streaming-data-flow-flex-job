package com.example.dataflow.generic.parser.impl;

import com.example.dataflow.generic.parser.MessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CsvMessageParser implements MessageParser {
    private List<String> columns = new ArrayList<>();
    private char delimiter = ',';
    private boolean hasHeader = false;
    private transient ObjectMapper mapper;

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, Object> config) {
        if (config == null) {
            return;
        }
        Object configuredDelimiter = config.get("delimiter");
        if (configuredDelimiter instanceof String) {
            String value = (String) configuredDelimiter;
            if (!value.isEmpty()) {
            delimiter = value.charAt(0);
            }
        }
        Object configuredHeader = config.get("hasHeader");
        if (configuredHeader instanceof Boolean) {
            Boolean value = (Boolean) configuredHeader;
            hasHeader = value;
        }
        Object configuredColumns = config.get("columns");
        if (configuredColumns instanceof List<?>) {
            columns = new ArrayList<>((List<String>) configuredColumns);
        }
    }

    @Override
    public JsonNode parse(byte[] payload) throws Exception {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        String raw = new String(payload, StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setTrim(true)
                .build();

        try (CSVParser parser = new CSVParser(new StringReader(raw), format)) {
            CSVRecord record = parser.getRecords().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Empty CSV payload"));
            ObjectNode output = mapper.createObjectNode();
            if (hasHeader) {
                throw new IllegalArgumentException("hasHeader=true is not supported for single-message parsing");
            }
            for (int i = 0; i < record.size(); i++) {
                String fieldName = i < columns.size() ? columns.get(i) : "field_" + i;
                output.put(fieldName, record.get(i));
            }
            return output;
        }
    }
}
