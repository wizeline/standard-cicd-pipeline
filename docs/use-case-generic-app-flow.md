# Use Case - generic app flow (CI)

This use case is used to test, lint and build a docker image for a specific git repository. After a successful build the image is pushed to a docker registry.


## Preconditions

- Valid git repo URL
- Submodules are all in the same repository manager (e.g. github)
- Branch(s) of interest have a `Dockerfile.unit-tests`
- Branch(s) of interest have a `Dockerfile.lint`
- Branch(s) of interest have a `Dockerfile`
- All required parameters are filled and reviewed: BRANCH, GIT_REPO_URL, GIT_CREDENTIALS_ID, DOCKER_IMAGE_NAME, SLACK_CHANNEL_NAME, DOCKER_SOURCE_REL_PATH


## Triggers

- Job build either by a user or by another job. BRANCH parameter has to be filled.


## Procedure

1. Git repo is checkout and git branch and git sha info is extracted. (D1)
2. A docker unit-test image is build, tagged and pushed to a repository. (D2)
3. The unit-test image is ran. (D3)
4. A docker lint image is build, tagged and pushed to a repository. (D4)
5. The lint image is ran. (D5)
6. If current branch is one of main branches. (D6)
7. Docker app image is build, tagged with commit sha, and pushed to a repository. (D7)
8. Job exit with success.


## Deviations

D1. If repo can't be cloned, accesses, branch, or sha does not exist, job exits and fails.
D2. If unit test image cannot be built, or `Dockerfile.unit-test` does not exist, job exits and fails.
D3. If unit-test container returns an exit code different than zero, job exits and fails.
D4. If lint image cannot be built, or `Dockerfile.lint` does not exist, job exits and fails.
D5. If lint container returns an exit code different than zero, job exits and fails.
D6. Job exit with success
D7. If app image cannot be built, or `Dockerfile` does not exist, job exits and fails.


## Other behaviors

- Slack notifications are sent when a docker build starts and finishes
- Slack notifications are sent when a docker runner finishes


## Dependencies

- genericDispatcher
- gitCheckout
- dockerBuilder
- dockerRunner
