//#!Groovy
def call(body) {

  def config = [:]

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
  def jobDockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'

  def is_main_branch(){
    return params.BRANCH == "origin/develop" &&
    params.BRANCH == "origin/stage" &&
    params.BRANCH == "origin/master"
  }

  stage("unit-tests:"){
    dockerBuilder {
        gitRepoUrl = jobGitRepoUrl
        gitCredentialsId = jobGitCredentialsId
        gitSha  = jobGitSha

        dockerImageName = jobDockerImageName
        dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
        slackChannelName = jobSlackChannelName

        dockerEnvTag = "test"
        dockerDockerfile = "Dockerfile.unit-tests"
        dockerNoTagCheck = "true"
        dockerSourceRelativePath = jobDockerSourceRelativePath
    }

    dockerRunner {
      dockerImageName = jobDockerImageName
      dockerImageTag = "test"
      dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
      slackChannelName = jobSlackChannelName
    }
  }

  stage("lint:"){
    dockerBuilder {
        gitRepoUrl = jobGitRepoUrl
        gitCredentialsId = jobGitCredentialsId
        gitSha  = jobGitSha

        dockerImageName = jobDockerImageName
        dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
        slackChannelName = jobSlackChannelName

        dockerEnvTag = "lint"
        dockerDockerfile = "Dockerfile.lint"
        dockerNoTagCheck = "true"
        dockerSourceRelativePath = jobDockerSourceRelativePath
    }

    dockerRunner {
      dockerImageName = jobDockerImageName
      dockerImageTag = "lint"
      dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
      slackChannelName = jobSlackChannelName
    }
  }

  if (is_main_branch()) {
    stage("build-image:") {
      dockerBuilder {
          gitRepoUrl = jobGitRepoUrl
          gitCredentialsId = jobGitCredentialsId
          gitSha  = jobGitSha

          dockerImageName = jobDockerImageName
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          slackChannelName = jobSlackChannelName

          dockerSourceRelativePath = jobDockerSourceRelativePath
      }
    }
  }

}
