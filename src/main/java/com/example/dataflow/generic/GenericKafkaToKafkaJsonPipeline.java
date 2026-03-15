package com.example.dataflow.generic;

import com.example.dataflow.generic.parser.ParserRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericKafkaToKafkaJsonPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(GenericKafkaToKafkaJsonPipeline.class);
    private static final TupleTag<KV<byte[], String>> SUCCESS_TAG = new TupleTag<>() {};
    private static final TupleTag<KV<byte[], String>> DLQ_TAG = new TupleTag<>() {};

    public static void main(String[] args) {
        GenericPipelineOptions options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .as(GenericPipelineOptions.class);

        FileSystems.setDefaultPipelineOptions(options);

        Pipeline pipeline = Pipeline.create(options);

        Map<String, Object> consumerConfig = new HashMap<>();
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        applySecurityConfig(options, consumerConfig);

        Map<String, Object> producerConfig = new HashMap<>();
        applySecurityConfig(options, producerConfig);

        PCollection<KV<byte[], byte[]>> input = pipeline.apply("ReadInputTopic",
                KafkaIO.<byte[], byte[]>read()
                        .withBootstrapServers(options.getBootstrapServers())
                        .withTopic(options.getInputTopic())
                        .withKeyDeserializer(ByteArrayDeserializer.class)
                        .withValueDeserializer(ByteArrayDeserializer.class)
                        .withConsumerConfigUpdates(consumerConfig)
                        .withoutMetadata());

        PCollectionTuple routed = input.apply("ParseToJson", ParDo.of(
            new ParseToJsonFn(
                options.getParserRegistryPath(),
                options.getDefaultMessageFormat()))
                .withOutputTags(SUCCESS_TAG, org.apache.beam.sdk.values.TupleTagList.of(DLQ_TAG)));

        routed.get(SUCCESS_TAG).apply("WriteOutputTopic",
                KafkaIO.<byte[], String>write()
                        .withBootstrapServers(options.getBootstrapServers())
                        .withTopic(options.getOutputTopic())
                        .withKeySerializer(ByteArraySerializer.class)
                        .withValueSerializer(StringSerializer.class)
                        .withProducerConfigUpdates(producerConfig));

        if (options.getDeadLetterTopic() != null && !options.getDeadLetterTopic().isBlank()) {
            routed.get(DLQ_TAG).apply("WriteDeadLetterTopic",
                    KafkaIO.<byte[], String>write()
                            .withBootstrapServers(options.getBootstrapServers())
                            .withTopic(options.getDeadLetterTopic())
                            .withKeySerializer(ByteArraySerializer.class)
                            .withValueSerializer(StringSerializer.class)
                            .withProducerConfigUpdates(producerConfig));
        }

        pipeline.run();
    }

    private static void applySecurityConfig(GenericPipelineOptions options, Map<String, Object> config) {
        if (options.getKafkaSecurityProtocol() != null && !options.getKafkaSecurityProtocol().isBlank()) {
            config.put("security.protocol", options.getKafkaSecurityProtocol());
        }
        if (options.getKafkaSaslMechanism() != null && !options.getKafkaSaslMechanism().isBlank()) {
            config.put("sasl.mechanism", options.getKafkaSaslMechanism());
        }
        if (options.getKafkaSaslJaasConfig() != null && !options.getKafkaSaslJaasConfig().isBlank()) {
            config.put("sasl.jaas.config", options.getKafkaSaslJaasConfig());
        }
    }

    private static class ParseToJsonFn extends DoFn<KV<byte[], byte[]>, KV<byte[], String>> {
        private final String parserRegistryPath;
        private final String defaultFormat;
        private transient ParserRegistry parserRegistry;
        private transient ObjectMapper mapper;

        private ParseToJsonFn(String parserRegistryPath, String defaultFormat) {
            this.parserRegistryPath = parserRegistryPath;
            this.defaultFormat = defaultFormat;
        }

        @Setup
        public void setup() {
            this.parserRegistry = ParserRegistry.load(parserRegistryPath);
            this.mapper = new ObjectMapper();
        }

        @ProcessElement
        public void processElement(ProcessContext context) {
            byte[] key = context.element().getKey();
            byte[] payload = context.element().getValue();

            try {
                JsonNode parsed = null;
                Exception lastError = null;

                for (String candidate : resolveCandidates(payload)) {
                    if (!parserRegistry.hasParser(candidate)) {
                        continue;
                    }
                    try {
                        parsed = parserRegistry.parse(candidate, payload);
                        break;
                    } catch (Exception ex) {
                        lastError = ex;
                    }
                }

                if (parsed == null) {
                    if (lastError != null) {
                        throw lastError;
                    }
                    throw new IllegalArgumentException("No configured parser could handle the payload");
                }

                String output = mapper.writeValueAsString(parsed);
                context.output(SUCCESS_TAG, KV.of(key, output));
            } catch (Exception ex) {
                LOG.error("Failed to parse message with default fallback format {}", defaultFormat, ex);
                String failedPayload = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
                String errorJson = String.format(
                        "{\"error\":\"%s\",\"defaultFormat\":\"%s\",\"payload\":%s}",
                        sanitize(ex.getMessage()),
                        sanitize(defaultFormat),
                        mapperValue(failedPayload));
                context.output(DLQ_TAG, KV.of(key, errorJson));
            }
        }

        private List<String> resolveCandidates(byte[] payload) {
            Set<String> candidates = new LinkedHashSet<>();
            candidates.add(resolveFormat(payload));

            if (defaultFormat != null && !defaultFormat.isBlank()) {
                candidates.add(defaultFormat.trim().toLowerCase());
            }

            candidates.addAll(parserRegistry.getConfiguredFormats());
            return List.copyOf(candidates);
        }

        private String resolveFormat(byte[] payload) {
            if (payload == null || payload.length == 0) {
                return defaultFormat;
            }

            String value = new String(payload, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                return defaultFormat;
            }

            // Heuristic format detection for mixed payloads in one topic.
            if (value.startsWith("{") || value.startsWith("[")) {
                return "json";
            }
            if (value.startsWith("<")) {
                return "xml";
            }
            if (value.contains(",")) {
                return "csv";
            }

            return "raw";
        }

        private String sanitize(String value) {
            if (value == null) {
                return "unknown";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private String mapperValue(String value) {
            try {
                return mapper.writeValueAsString(value);
            } catch (Exception e) {
                return "\"\"";
            }
        }
    }
}
