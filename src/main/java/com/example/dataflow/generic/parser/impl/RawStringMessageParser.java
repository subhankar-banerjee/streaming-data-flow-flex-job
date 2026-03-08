package com.example.dataflow.generic.parser.impl;

import com.example.dataflow.generic.parser.MessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;

public class RawStringMessageParser implements MessageParser {
    private transient ObjectMapper mapper;

    @Override
    public JsonNode parse(byte[] payload) {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("raw", new String(payload, StandardCharsets.UTF_8));
        return out;
    }
}
