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
  def jobJenkinsNode = config.jobJenkinsNode

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
      def test_tag = "unit-test-${env.BUILD_NUMBER}"
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
          jenkinsNode = jobJenkinsNode
      }

      dockerRunner {
        dockerImageName = jobDockerImageName
        dockerImageTag = test_tag
        dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
        slackChannelName = jobSlackChannelName

        dockerDaemonHost = jobDockerDaemonHost
        jenkinsNode = jobJenkinsNode
      }
      return_hash["unit-tests"] = "success"
    }
  }

  tasks["lint"] = {
    stage("lint:"){
      def lint_tag = "lint-${env.BUILD_NUMBER}"
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
          jenkinsNode = jobJenkinsNode
      }

      dockerRunner {
        dockerImageName = jobDockerImageName
        dockerImageTag = lint_tag
        dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
        slackChannelName = jobSlackChannelName

        dockerDaemonHost = jobDockerDaemonHost
        jenkinsNode = jobJenkinsNode
      }
      return_hash["lint"] = "success"
    }
  }

  parallel tasks

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
          jenkinsNode = jobJenkinsNode
      }
      return_hash["build-image"] = "success"
    }
  }

  return return_hash

}
