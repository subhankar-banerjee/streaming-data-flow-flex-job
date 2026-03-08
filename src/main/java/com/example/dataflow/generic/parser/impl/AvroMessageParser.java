package com.example.dataflow.generic.parser.impl;

import com.example.dataflow.generic.parser.MessageParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;

public class AvroMessageParser implements MessageParser {
    private String topic = "dataflow-input";
    private transient KafkaAvroDeserializer deserializer;
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
        copyIfPresent(config, deserializerConfig, "schemaRegistryUrl", KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG);
        copyIfPresent(config, deserializerConfig, "basicAuthCredentialsSource", "basic.auth.credentials.source");
        copyIfPresent(config, deserializerConfig, "schemaRegistryBasicAuthUserInfo", "schema.registry.basic.auth.user.info");
        copyIfPresent(config, deserializerConfig, "bearerAuthToken", "bearer.auth.token");

        Object configuredUseLatest = config.get("useLatestVersion");
        if (configuredUseLatest instanceof Boolean) {
            deserializerConfig.put(KafkaAvroDeserializerConfig.USE_LATEST_VERSION, configuredUseLatest);
        }

        if (!deserializerConfig.containsKey(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG)) {
            throw new IllegalArgumentException("Avro parser requires schemaRegistryUrl in parser config");
        }

        deserializer = new KafkaAvroDeserializer();
        deserializer.configure(deserializerConfig, false);
    }

    @Override
    public JsonNode parse(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Empty payload");
        }
        if (deserializer == null) {
            throw new IllegalStateException("Avro parser is not configured");
        }
        if (mapper == null) {
            mapper = new ObjectMapper();
        }

        Object parsed = deserializer.deserialize(topic, payload);
        if (parsed == null) {
            throw new IllegalArgumentException("Avro deserializer returned null");
        }

        if (parsed instanceof GenericRecord) {
            return mapper.readTree(parsed.toString());
        }
        return mapper.valueToTree(parsed);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value instanceof String && !((String) value).isBlank()) {
            target.put(targetKey, ((String) value).trim());
        }
    }
}
