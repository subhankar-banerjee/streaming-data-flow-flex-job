variable "enable_cmek" {
  description = "Enable customer-managed encryption key for storage bucket"
  type        = bool
  default     = true
}

variable "kms_key_ring_name" {
  description = "KMS key ring name"
  type        = string
  default     = "dataflow-keyring"
}

variable "kms_crypto_key_name" {
  description = "KMS crypto key name"
  type        = string
  default     = "dataflow-key"
}

variable "kms_rotation_period" {
  description = "KMS crypto key rotation period"
  type        = string
  default     = "7776000s"
}

data "google_project" "current" {
  project_id = var.project_id
}

resource "google_kms_key_ring" "dataflow_ring" {
  count    = var.enable_cmek ? 1 : 0
  name     = var.kms_key_ring_name
  location = var.region
  project  = var.project_id
}

resource "google_kms_crypto_key" "dataflow_key" {
  count           = var.enable_cmek ? 1 : 0
  name            = var.kms_crypto_key_name
  key_ring        = google_kms_key_ring.dataflow_ring[0].id
  rotation_period = var.kms_rotation_period
}

resource "google_kms_crypto_key_iam_member" "dataflow_service_agent" {
  count = var.enable_cmek ? 1 : 0

  crypto_key_id = google_kms_crypto_key.dataflow_key[0].id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:service-${data.google_project.current.number}@dataflow-service-producer-prod.iam.gserviceaccount.com"
}
