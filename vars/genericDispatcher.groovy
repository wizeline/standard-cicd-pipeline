//#!Groovy

def is_main_branch(){
  return params.BRANCH == "origin/develop" ||
  params.BRANCH == "origin/stage" ||
  params.BRANCH == "origin/master" ||
  params.BRANCH == "develop" ||
  params.BRANCH == "stage" ||
  params.BRANCH == "master"
}

def call(body) {

  def config = [:]
  def return_hash = [:]
  def tasks = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  print config

  def jobGitRepoUrl = params.GIT_REPO_URL
  def jobGitCredentialsId = params.GIT_CREDENTIALS_ID
  def jobGitSha = params.BRANCH
  def jobDisableSubmodules = config.disableSubmodules
  def jobDockerImageName = params.DOCKER_IMAGE_NAME
  def jobSlackChannelName = params.SLACK_CHANNEL_NAME
  def jobDockerSourceRelativePath = params.DOCKER_SOURCE_REL_PATH
  def jobDockerRegistryCredentialsId = params.DOCKER_REG_CREDENTIALS_ID ?: 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
  def jobGitShaNoOrigin = jobGitSha//.replace("origin/", "")
  def jobDockerDaemonHost = config.jobDockerDaemonHost
  def jobDockerDaemonPort = config.dockerDaemonPort ?: '4243'
  def jobJenkinsNode = config.jobJenkinsNode

  def disableLint = config.disableLint ?: 'false'
  def disableUnitTests = config.disableUnitTests ?: 'false'

  def jobGitBranch
  def jobGitShaCommit

  node {
    stage ('Checkout') {
      //git branch: jobGitShaNoOrigin, url: jobGitRepoUrl, credentialsId: jobGitCredentialsId
      git_info = gitCheckout {
        branch = jobGitShaNoOrigin
        credentialsId = jobGitCredentialsId
        repoUrl = jobGitRepoUrl
        disableSubmodules = jobDisableSubmodules
      }
      jobGitBranch = git_info["git-branch"]
      jobGitShaCommit = git_info["git-commit-sha"]

      return_hash["git-branch"] = jobGitBranch
      return_hash["git-sha"] = jobGitShaCommit

      echo "Branch: ${jobGitBranch}"
      echo "SHA: ${jobGitShaCommit}"
    }
  }

  tasks["unit_tests"] = {
    stage("unit-tests:"){
      if (disableUnitTests != 'true'){
        def test_tag = "unit-test"
        dockerBuilder {
            gitRepoUrl = jobGitRepoUrl
            gitCredentialsId = jobGitCredentialsId
            gitSha  = jobGitShaCommit
            disableSubmodules = jobDisableSubmodules

            dockerImageName = jobDockerImageName
            dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
            slackChannelName = jobSlackChannelName

            dockerEnvTag = test_tag
            dockerDockerfile = "Dockerfile.unit-tests"
            dockerNoTagCheck = "true"
            dockerSourceRelativePath = jobDockerSourceRelativePath

            // dockerDaemonUrl vs dockerDaemonHost
            // dockerDaemonUrl: will select a dockerd from a elb
            // dockerDaemonHost: uses specific dockerd
            dockerDaemonHost = jobDockerDaemonHost
            dockerDaemonPort = jobDockerDaemonPort
            jenkinsNode = jobJenkinsNode
        }

        dockerRunner {
          dockerImageName = jobDockerImageName
          dockerImageTag = test_tag
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          slackChannelName = jobSlackChannelName

          dockerDaemonHost = jobDockerDaemonHost
          dockerDaemonPort = jobDockerDaemonPort
          jenkinsNode = jobJenkinsNode
        }
        return_hash["unit-tests"] = "success"
      } else {
        // mark stage as not done
        return_hash["unit-tests"] = "not-run"
        echo "UNSTABLE"
        currentBuild.result = 'UNSTABLE'
      }
    }
  }

  tasks["lint"] = {
    stage("lint:"){
      if (disableLint != 'true'){
        def lint_tag = "lint"
        dockerBuilder {
            gitRepoUrl = jobGitRepoUrl
            gitCredentialsId = jobGitCredentialsId
            gitSha  = jobGitShaCommit
            disableSubmodules = jobDisableSubmodules

            dockerImageName = jobDockerImageName
            dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
            slackChannelName = jobSlackChannelName

            dockerEnvTag = lint_tag
            dockerDockerfile = "Dockerfile.lint"
            dockerNoTagCheck = "true"
            dockerSourceRelativePath = jobDockerSourceRelativePath

            dockerDaemonHost = jobDockerDaemonHost
            dockerDaemonPort = jobDockerDaemonPort
            jenkinsNode = jobJenkinsNode
        }

        dockerRunner {
          dockerImageName = jobDockerImageName
          dockerImageTag = lint_tag
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          slackChannelName = jobSlackChannelName

          dockerDaemonHost = jobDockerDaemonHost
          dockerDaemonPort = jobDockerDaemonPort
          jenkinsNode = jobJenkinsNode
        }
        return_hash["lint"] = "success"
      } else {
        // mark stage as not done
        return_hash["lint"] = "not-run"
        echo "UNSTABLE"
        currentBuild.result = 'UNSTABLE'
      }
    }
  }

  parallel tasks

  // If unit-test or lint was skipped, handle success
  if (
    return_hash["unit-tests"] == "success" && return_hash["lint"] == "not-run" ||
    return_hash["lint"] == "success" && return_hash["unit-tests"] == "not-run"){
    echo "SUCCESS"
    currentBuild.result = 'SUCCESS'
  }

  if (is_main_branch()) {
    stage("build-image:") {
      dockerBuilder {
          gitRepoUrl = jobGitRepoUrl
          gitCredentialsId = jobGitCredentialsId
          gitSha  = jobGitShaCommit
          disableSubmodules = jobDisableSubmodules

          dockerImageName = jobDockerImageName
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          slackChannelName = jobSlackChannelName

          dockerSourceRelativePath = jobDockerSourceRelativePath

          dockerDaemonHost = jobDockerDaemonHost
          dockerDaemonPort = jobDockerDaemonPort
          jenkinsNode = jobJenkinsNode
      }
      return_hash["build-image"] = "success"
    }
  }

  return return_hash

}
