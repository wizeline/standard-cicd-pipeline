# Use Case - generic terraform flow (CI)

This use case is used to plan, apply and destroy cloud resources using terraform.


## Preconditions

- Valid git repo URL
- Submodules are all in the same repository manager (e.g. github)
- All required parameters are filled and reviewed (1): TERRAFORM_COMMAND, TF_VARS, GIT_REPO_URL, GIT_CREDENTIALS_ID, GIT_SHA
- All required parameters are filled and reviewed (2): jobTfSourceRelativePath, jobTfAwsAccessCredentialsId, jobTfAwsRegion, jobTfAwsBackendBucketName, jobTfAwsBackendBucketRegion, jobTfAwsBackendBucketKeyPath
- A valid s3 bucket with a specific path for TF resources
- Valid AWS credentials for TF to operate
- Terraform script have s3 backend configured by ENV vars.

## Triggers

- Job build either by a user or by another job.


## Procedure

1. TERRAFORM_COMMAND is selected to PLAN_APPLY (PLAN_DESTROY).
2. Git repo is cloned and copied into the terraform runner container. (D1)
3. Terraform `plan` (`plan_destroy`) command is executed and exit code is returned. Current plan is stored in the same path as in the `tf.state` file in s3. (D2, D3)
4. exit code is 2, there are changes in the plan, plan is printed.
5. A notification for approve plan is sent via Slack and confirmation is waited.
6. Plan is confirmed, terraform `apply` (`destroy`) command is executed and exit code is returned. (D4, D3)
7. exit code is 0, job success.


## Deviations

D1. If repo can't be cloned, accesses, branch, or sha does not exist, job exits and fails.
D2. exit code is 0, there are no changes to apply, job exit with success.
D3. exit code is different than 0 or 2, there was an error, job fails and exits.
D4. confirmation is not approved, build is marked as unstable and job exits.


## Other behaviors

- Slack notifications are sent when build starts
- Slack notifications are sent when build finishes


## Dependencies

- terraformControl
- dockerTerraformRunner
- gitCheckout
