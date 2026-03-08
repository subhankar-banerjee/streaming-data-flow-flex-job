package com.example.dataflow.generic.parser;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.Map;

public interface MessageParser extends Serializable {
    default void configure(Map<String, Object> config) {
    }

    JsonNode parse(byte[] payload) throws Exception;
}
