package com.example.dataflow.generic.parser.impl;

import com.example.dataflow.generic.parser.MessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class XmlMessageParser implements MessageParser {
    private transient XmlMapper mapper;

    @Override
    public JsonNode parse(byte[] payload) throws Exception {
        if (mapper == null) {
            mapper = new XmlMapper();
        }
        return mapper.readTree(payload);
    }
}
