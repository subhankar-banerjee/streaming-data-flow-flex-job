output "harness_workload_identity_pool_name" {
  description = "Full resource name of the Workload Identity Pool"
  value       = var.enable_workload_identity ? google_iam_workload_identity_pool.harness[0].name : null
}

output "harness_workload_identity_provider_name" {
  description = "Full resource name of the Workload Identity Provider"
  value       = var.enable_workload_identity ? google_iam_workload_identity_pool_provider.harness_oidc[0].name : null
}

output "harness_runner_service_account_email" {
  description = "Service account email to impersonate from Harness"
  value       = var.enable_workload_identity ? google_service_account.terraform_runner[0].email : null
}

output "harness_wif_principal_set" {
  description = "Principal set path that has workloadIdentityUser on the runner SA"
  value       = var.enable_workload_identity ? "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.harness[0].name}/*" : null
}
