package com.example.dataflow.generic.parser.impl;

import com.example.dataflow.generic.parser.MessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import java.util.HashMap;
import java.util.Map;

public class ProtobufMessageParser implements MessageParser {
    private String topic = "dataflow-input";
    private transient KafkaProtobufDeserializer<Message> deserializer;
    private transient ObjectMapper mapper;

    @Override
    public void configure(Map<String, Object> config) {
        if (config == null) {
            config = new HashMap<>();
        }

        Object configuredTopic = config.get("topic");
        if (configuredTopic instanceof String && !((String) configuredTopic).isBlank()) {
            topic = ((String) configuredTopic).trim();
        }

        Map<String, Object> deserializerConfig = new HashMap<>();
        copyIfPresent(config, deserializerConfig, "schemaRegistryUrl", "schema.registry.url");
        copyIfPresent(config, deserializerConfig, "basicAuthCredentialsSource", "basic.auth.credentials.source");
        copyIfPresent(config, deserializerConfig, "schemaRegistryBasicAuthUserInfo", "schema.registry.basic.auth.user.info");
        copyIfPresent(config, deserializerConfig, "bearerAuthToken", "bearer.auth.token");

        if (!deserializerConfig.containsKey("schema.registry.url")) {
            throw new IllegalArgumentException("Protobuf parser requires schemaRegistryUrl in parser config");
        }

        deserializer = new KafkaProtobufDeserializer<>();
        deserializer.configure(deserializerConfig, false);
    }

    @Override
    public JsonNode parse(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Empty payload");
        }
        if (deserializer == null) {
            throw new IllegalStateException("Protobuf parser is not configured");
        }
        if (mapper == null) {
            mapper = new ObjectMapper();
        }

        Message message = deserializer.deserialize(topic, payload);
        if (message == null) {
            throw new IllegalArgumentException("Protobuf deserializer returned null");
        }

        String json = JsonFormat.printer().print(message);
        return mapper.readTree(json);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value instanceof String && !((String) value).isBlank()) {
            target.put(targetKey, ((String) value).trim());
        }
    }
}
