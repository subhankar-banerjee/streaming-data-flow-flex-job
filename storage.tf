variable "template_bucket_name" {
  description = "GCS bucket used to store Flex template specs"
  type        = string
}

variable "staging_bucket_name" {
  description = "GCS bucket used by Dataflow for staging/temp artifacts"
  type        = string
}

variable "force_destroy_bucket" {
  description = "Allow terraform destroy even if bucket contains objects"
  type        = bool
  default     = false
}

resource "google_storage_bucket" "template_specs" {
  name                        = var.template_bucket_name
  project                     = var.project_id
  location                    = var.region
  storage_class               = "STANDARD"
  force_destroy               = var.force_destroy_bucket
  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  dynamic "encryption" {
    for_each = var.enable_cmek ? [1] : []
    content {
      default_kms_key_name = google_kms_crypto_key.dataflow_key[0].id
    }
  }
}

resource "google_storage_bucket" "dataflow_staging" {
  name                        = var.staging_bucket_name
  project                     = var.project_id
  location                    = var.region
  storage_class               = "STANDARD"
  force_destroy               = var.force_destroy_bucket
  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  dynamic "encryption" {
    for_each = var.enable_cmek ? [1] : []
    content {
      default_kms_key_name = google_kms_crypto_key.dataflow_key[0].id
    }
  }

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "Delete"
    }
  }
}
