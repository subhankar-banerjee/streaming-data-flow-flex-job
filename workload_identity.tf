variable "enable_workload_identity" {
  description = "Enable Workload Identity Federation resources for external OIDC (Harness)."
  type        = bool
  default     = true
}

variable "wif_pool_id" {
  description = "Workload Identity Pool ID (4-32 chars, lowercase, digits, hyphen)."
  type        = string
  default     = "harness-pool"
}

variable "wif_provider_id" {
  description = "Workload Identity Provider ID (4-32 chars, lowercase, digits, hyphen)."
  type        = string
  default     = "harness-oidc"
}

variable "harness_oidc_issuer_uri" {
  description = "OIDC issuer URI used by Harness (for example: https://app.harness.io/ng/api/oidc/account/<ACCOUNT_ID>)."
  type        = string
}

variable "harness_allowed_audiences" {
  description = "Allowed OIDC audiences expected from Harness tokens."
  type        = list(string)
  default     = []
}

variable "harness_attribute_condition" {
  description = "Optional CEL condition to restrict accepted Harness tokens. Leave empty to allow all from issuer."
  type        = string
  default     = ""
}

variable "terraform_runner_sa_id" {
  description = "Service account id used by Harness to impersonate for Terraform/Dataflow operations."
  type        = string
  default     = "harness-terraform-runner"
}

variable "terraform_runner_sa_roles" {
  description = "Project-level roles for the Harness runner service account."
  type        = list(string)
  default = [
    "roles/dataflow.admin",
    "roles/dataflow.worker",
    "roles/storage.admin",
    "roles/artifactregistry.reader",
    "roles/artifactregistry.writer",
    "roles/iam.serviceAccountUser",
    "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  ]
}

resource "google_service_account" "terraform_runner" {
  count = var.enable_workload_identity ? 1 : 0

  project      = var.project_id
  account_id   = var.terraform_runner_sa_id
  display_name = "Harness Terraform Runner"
}

resource "google_iam_workload_identity_pool" "harness" {
  count = var.enable_workload_identity ? 1 : 0

  project                   = var.project_id
  workload_identity_pool_id = var.wif_pool_id
  display_name              = "Harness OIDC Pool"
  description               = "Workload Identity Pool for Harness OIDC federation"
}

resource "google_iam_workload_identity_pool_provider" "harness_oidc" {
  count = var.enable_workload_identity ? 1 : 0

  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.harness[0].workload_identity_pool_id
  workload_identity_pool_provider_id = var.wif_provider_id
  display_name                       = "Harness OIDC Provider"
  description                        = "OIDC provider for Harness pipelines"
  attribute_condition                = var.harness_attribute_condition != "" ? var.harness_attribute_condition : null

  attribute_mapping = {
    "google.subject"      = "assertion.sub"
    "attribute.aud"       = "assertion.aud"
    "attribute.issuer"    = "assertion.iss"
    "attribute.actor"     = "assertion.actor"
    "attribute.principal" = "assertion.sub"
  }

  oidc {
    issuer_uri        = var.harness_oidc_issuer_uri
    allowed_audiences = var.harness_allowed_audiences
  }
}

resource "google_service_account_iam_member" "harness_wif_user" {
  count = var.enable_workload_identity ? 1 : 0

  service_account_id = google_service_account.terraform_runner[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.harness[0].name}/*"
}

resource "google_project_iam_member" "terraform_runner_project_roles" {
  for_each = var.enable_workload_identity ? toset(var.terraform_runner_sa_roles) : toset([])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.terraform_runner[0].email}"
}
