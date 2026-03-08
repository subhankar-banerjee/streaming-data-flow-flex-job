# Generic Dataflow Flex Template (Kafka -> JSON -> Kafka)

This project provides a **single reusable Java pipeline** for multiple teams.

## What is generic in this implementation

- No hardcoded topic names in code.
- No hardcoded schema, format, or business rules in code.
- Runtime-controlled behavior through:
  - Dataflow runtime parameters
  - External parser registry config file
  - Pluggable parser classes (`MessageParser` interface)

## Architecture

1. Read raw bytes from Confluent Kafka input topic.
2. Resolve parser from registry using `defaultMessageFormat` runtime parameter.
3. Convert message to canonical JSON string.
4. Publish JSON to output topic.
5. Publish parse failures to optional dead-letter topic.

For mixed message types in one topic, set `formatDetectionMode=auto`.
The pipeline then detects `json`, `xml`, or `csv` per message and falls back to `defaultMessageFormat`.

## Project structure

- `src/main/java/.../GenericKafkaToKafkaJsonPipeline.java` - pipeline entry point
- `src/main/java/.../GenericPipelineOptions.java` - runtime parameters
- `src/main/java/.../parser/*` - parser registry and parser interface
- `src/main/java/.../parser/impl/*` - example parser plugins (json, csv, xml, raw)
- `config/parser-registry.example.yaml` - sample parser registry
- `flex/metadata.json` - Flex Template metadata (parameter UI/validation)
- `scripts/register-flex-template.ps1` - register/update Flex Template spec using an existing image URI
- `scripts/run-flex-template.ps1` - launch Dataflow job with parameters

## Prerequisites

- Java 11
- Maven 3.9+
- `gcloud` CLI authenticated
- GCP project with Dataflow, Artifact Registry, and GCS bucket

## Build pipeline JAR

```powershell
mvn clean package -DskipTests
```

## Register Flex Template spec

Use an already published image URI in GAR.

```powershell
./scripts/register-flex-template.ps1 `
  -ImageUri "europe-west1-docker.pkg.dev/<PROJECT_ID>/<GAR_REPO>/generic-kafka-json-template:v3" `
  -TemplateSpecGcsPath "gs://<BUCKET>/templates/generic-kafka-json-template-v3.json"
```

## Run Dataflow Flex Template job

```powershell
./scripts/run-flex-template.ps1 `
  -ProjectId "<PROJECT_ID>" `
  -Region "europe-west1" `
  -TemplateSpecGcsPath "gs://<BUCKET>/templates/generic-kafka-json-template-v3.json" `
  -JobName "kafka-json-router-$(Get-Date -Format 'yyyyMMdd-HHmmss')" `
  -BootstrapServers "pkc-xxxxx.europe-west1.gcp.confluent.cloud:9092" `
  -InputTopic "teamA-input" `
  -OutputTopic "teamA-output-json" `
  -ParserRegistryPath "gs://<BUCKET>/config/parser-registry.yaml" `
  -DefaultMessageFormat "json" `
  -FormatDetectionMode "auto" `
  -DeadLetterTopic "teamA-dlq" `
  -KafkaSecurityProtocol "SASL_SSL" `
  -KafkaSaslMechanism "PLAIN" `
  -KafkaSaslJaasConfig "org.apache.kafka.common.security.plain.PlainLoginModule required username='***' password='***';" `
  -StagingLocation "gs://<BUCKET>/dataflow/staging" `
  -TempLocation "gs://<BUCKET>/dataflow/temp" `
  -ServiceAccountEmail "<DATAFLOW_WORKER_SERVICE_ACCOUNT_EMAIL>"
```

`FormatDetectionMode` values:
- `fixed`: Uses `DefaultMessageFormat` for all messages.
- `auto`: Detects `json`, `xml`, `csv` per message and falls back to `DefaultMessageFormat`.

## Parser registry model

Example (`config/parser-registry.example.yaml`):

```yaml
parsers:
  - format: json
    className: com.example.dataflow.generic.parser.impl.JsonMessageParser
    config: {}
  - format: csv
    className: com.example.dataflow.generic.parser.impl.CsvMessageParser
    config:
      delimiter: ","
      columns: [id, name, amount]
```

To support a new format:
1. Add a new parser class implementing `MessageParser`.
2. Include class in build artifact.
3. Add a new parser entry in registry config.
4. Run pipeline with `defaultMessageFormat=<new-format>`.

## Important operational note

For Flex Templates, consumers do **not** pull the image manually.
Dataflow service pulls the image from GAR when the job is launched using the Flex Template spec.

You can launch via:
- `gcloud dataflow flex-template run ...`
- Dataflow REST API
