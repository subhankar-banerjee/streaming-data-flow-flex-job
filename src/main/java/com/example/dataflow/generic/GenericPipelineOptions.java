package com.example.dataflow.generic;

import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

public interface GenericPipelineOptions extends DataflowPipelineOptions {

    @Description("Kafka bootstrap servers, e.g. host1:9092,host2:9092")
    @Validation.Required
    String getBootstrapServers();
    void setBootstrapServers(String value);

    @Description("Input Kafka topic")
    @Validation.Required
    String getInputTopic();
    void setInputTopic(String value);

    @Description("Output Kafka topic")
    @Validation.Required
    String getOutputTopic();
    void setOutputTopic(String value);

    @Description("Optional dead-letter Kafka topic for parse failures")
    String getDeadLetterTopic();
    void setDeadLetterTopic(String value);

    @Description("Path to parser registry yaml/json file")
    @Default.String("classpath:parser-registry.default.yaml")
    String getParserRegistryPath();
    void setParserRegistryPath(String value);

    @Description("Optional fallback message format key when payload format cannot be detected or detected parser fails, e.g. json, csv, xml")
    @Default.String("json")
    String getDefaultMessageFormat();
    void setDefaultMessageFormat(String value);

    @Description("Optional Kafka security.protocol")
    String getKafkaSecurityProtocol();
    void setKafkaSecurityProtocol(String value);

    @Description("Optional Kafka sasl.mechanism")
    String getKafkaSaslMechanism();
    void setKafkaSaslMechanism(String value);

    @Description("Optional Kafka sasl.jaas.config")
    String getKafkaSaslJaasConfig();
    void setKafkaSaslJaasConfig(String value);
}
