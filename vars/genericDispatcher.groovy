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

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  print config

  def jobGitRepoUrl = params.GIT_REPO_URL
  def jobGitCredentialsId = params.GIT_CREDENTIALS_ID
  def jobGitSha = params.BRANCH
  def jobDockerImageName = params.DOCKER_IMAGE_NAME
  def jobSlackChannelName = params.SLACK_CHANNEL_NAME
  def jobDockerSourceRelativePath = params.DOCKER_SOURCE_REL_PATH
  def jobDockerRegistryCredentialsId = params.DOCKER_REG_CREDENTIALS_ID ?: 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
  def jobGitShaNoOrigin = jobGitSha.replace("origin/", "")
  def jobDockerDaemonHost = config.jobDockerDaemonHost

  def jobGitBranch
  def jobGitShaCommit

  node {
    stage ('Checkout') {
      git branch: jobGitSha, url: jobGitRepoUrl, credentialsId: jobGitCredentialsId
      jobGitBranch = sh(returnStdout:true, script:'git rev-parse --abbrev-ref HEAD').trim()
      jobGitShaCommit = sh(returnStdout:true, script:'git rev-parse HEAD').trim()

      return_hash["git-branch"] = jobGitBranch
      return_hash["git-sha"] = jobGitShaCommit

      echo "Branch: ${jobGitBranch}"
      echo "SHA: ${jobGitShaCommit}"
    }
  }

  stage("unit-tests:"){
    dockerBuilder {
        gitRepoUrl = jobGitRepoUrl
        gitCredentialsId = jobGitCredentialsId
        gitSha  = jobGitShaNoOrigin

        dockerImageName = jobDockerImageName
        dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
        slackChannelName = jobSlackChannelName

        dockerEnvTag = "test"
        dockerDockerfile = "Dockerfile.unit-tests"
        dockerNoTagCheck = "true"
        dockerSourceRelativePath = jobDockerSourceRelativePath

        // dockerDaemonUrl vs dockerDaemonHost
        // dockerDaemonUrl: will select a dockerd from a elb
        // dockerDaemonHost: uses specific dockerd
        dockerDaemonHost = jobDockerDaemonHost
    }

    dockerRunner {
      dockerImageName = jobDockerImageName
      dockerImageTag = "test"
      dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
      slackChannelName = jobSlackChannelName

      dockerDaemonHost = jobDockerDaemonHost
    }
    return_hash["unit-tests"] = "success"
  }

  stage("lint:"){
    dockerBuilder {
        gitRepoUrl = jobGitRepoUrl
        gitCredentialsId = jobGitCredentialsId
        gitSha  = jobGitShaNoOrigin

        dockerImageName = jobDockerImageName
        dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
        slackChannelName = jobSlackChannelName

        dockerEnvTag = "lint"
        dockerDockerfile = "Dockerfile.lint"
        dockerNoTagCheck = "true"
        dockerSourceRelativePath = jobDockerSourceRelativePath

        dockerDaemonHost = jobDockerDaemonHost
    }

    dockerRunner {
      dockerImageName = jobDockerImageName
      dockerImageTag = "lint"
      dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
      slackChannelName = jobSlackChannelName

      dockerDaemonHost = jobDockerDaemonHost
    }
    return_hash["lint"] = "success"
  }

  if (is_main_branch()) {
    stage("build-image:") {
      dockerBuilder {
          gitRepoUrl = jobGitRepoUrl
          gitCredentialsId = jobGitCredentialsId
          gitSha  = jobGitShaNoOrigin

          dockerImageName = jobDockerImageName
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          slackChannelName = jobSlackChannelName

          dockerSourceRelativePath = jobDockerSourceRelativePath

          dockerDaemonHost = jobDockerDaemonHost
      }
      return_hash["build-image"] = "success"
    }
  }

  return return_hash

}
