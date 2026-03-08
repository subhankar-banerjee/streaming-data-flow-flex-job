package com.example.dataflow.generic.parser.impl;

import com.example.dataflow.generic.parser.MessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonMessageParser implements MessageParser {
    private transient ObjectMapper mapper;

    @Override
    public JsonNode parse(byte[] payload) throws Exception {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return mapper.readTree(payload);
    }
}
