terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

variable "project_id" {
  description = "GCP project id"
  type        = string
}

variable "region" {
  description = "Dataflow region"
  type        = string
  default     = "europe-west1"
}

variable "environment" {
  description = "Environment label, e.g. dev/qa/prod"
  type        = string
  default     = "dev"
}

variable "dataflow_job_name" {
  description = "Dataflow job name (must be unique for each active job)"
  type        = string
}

variable "template_spec_gcs_path" {
  description = "Optional full Flex template spec path in GCS. If empty, path is built from template_bucket_name + template_spec_object."
  type        = string
  default     = ""
}

variable "template_spec_object" {
  description = "Object path of the flex template spec inside template bucket"
  type        = string
  default     = "templates/generic-kafka-json-template-v4.json"
}

variable "dataflow_staging_prefix" {
  description = "Folder prefix inside staging bucket for Dataflow staging files"
  type        = string
  default     = "dataflow/staging"
}

variable "dataflow_temp_prefix" {
  description = "Folder prefix inside staging bucket for Dataflow temp files"
  type        = string
  default     = "dataflow/temp"
}

variable "bootstrap_servers" {
  description = "Confluent Kafka bootstrap servers"
  type        = string
}

variable "input_topic" {
  description = "ODP input topic"
  type        = string
}

variable "output_topic" {
  description = "FDP output topic"
  type        = string
}

variable "dead_letter_topic" {
  description = "Optional DLQ topic"
  type        = string
  default     = ""
}

variable "kafka_security_protocol" {
  description = "Kafka security.protocol"
  type        = string
  default     = "SASL_SSL"
}

variable "kafka_sasl_mechanism" {
  description = "Kafka sasl.mechanism"
  type        = string
  default     = "PLAIN"
}

variable "kafka_sasl_jaas_config" {
  description = "Kafka SASL JAAS config"
  type        = string
  sensitive   = true
}

variable "parser_registry_path" {
  description = "Optional parser registry path (local/classpath/gs://). Leave empty to use template default."
  type        = string
  default     = ""
}

variable "default_message_format" {
  description = "Optional fallback format. Leave empty to use template default."
  type        = string
  default     = ""
}

variable "extra_parameters" {
  description = "Optional extra Dataflow template parameters"
  type        = map(string)
  default     = {}
}

locals {
  computed_template_spec_gcs_path = var.template_spec_gcs_path != "" ? var.template_spec_gcs_path : "gs://${var.template_bucket_name}/${var.template_spec_object}"

  optional_parameters = merge(
    var.dead_letter_topic != "" ? { deadLetterTopic = var.dead_letter_topic } : {},
    var.parser_registry_path != "" ? { parserRegistryPath = var.parser_registry_path } : {},
    var.default_message_format != "" ? { defaultMessageFormat = var.default_message_format } : {}
  )

  dataflow_parameters = merge(
    {
      bootstrapServers    = var.bootstrap_servers
      inputTopic          = var.input_topic
      outputTopic         = var.output_topic
      stagingLocation     = "gs://${var.staging_bucket_name}/${var.dataflow_staging_prefix}"
      tempLocation        = "gs://${var.staging_bucket_name}/${var.dataflow_temp_prefix}"
      kafkaSecurityProtocol = var.kafka_security_protocol
      kafkaSaslMechanism  = var.kafka_sasl_mechanism
      kafkaSaslJaasConfig = var.kafka_sasl_jaas_config
    },
    local.optional_parameters,
    var.extra_parameters
  )
}

resource "google_dataflow_flex_template_job" "kafka_router" {
  project                 = var.project_id
  region                  = var.region
  name                    = var.dataflow_job_name
  container_spec_gcs_path = local.computed_template_spec_gcs_path
  on_delete               = "cancel"

  parameters = local.dataflow_parameters

  labels = {
    app = "generic-kafka-json-template"
    env = var.environment
  }
}
