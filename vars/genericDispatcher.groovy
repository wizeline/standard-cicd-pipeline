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

  stage("unit-tests"){
    dockerBuilder {
        gitRepoUrl = "git@github.com:wizeline/wz-statuspage.git"
        gitCredentialsId = 'b9abf261-0552-45f2-972d-08f3800d3d4f'
        gitSha  = "develop"

        dockerImageName = "cachet-backend"
        dockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
        slackChannelName = 'jenkins'

        dockerEnvTag = "test"
        dockerDockerfile = "Dockerfile.unit-tests"
        dockerNoTagCheck = "true"
        dockerSourceRelativePath = "backend"
    }

    dockerRunner {
      dockerImageName = "cachet-backend"
      dockerImageTag = "test"
      dockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
      slackChannelName = 'jenkins'
    }
  }

  stage("lint"){
    dockerBuilder {
        gitRepoUrl = "git@github.com:wizeline/wz-statuspage.git"
        gitCredentialsId = 'b9abf261-0552-45f2-972d-08f3800d3d4f'
        gitSha  = "develop"

        dockerImageName = "cachet-backend"
        dockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
        slackChannelName = 'jenkins'

        dockerEnvTag = "lint"
        dockerDockerfile = "Dockerfile.lint"
        dockerNoTagCheck = "true"
        dockerSourceRelativePath = "backend"
    }

    dockerRunner {
      dockerImageName = "cachet-backend"
      dockerImageTag = "lint"
      dockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
      slackChannelName = 'jenkins'
    }
  }

  stage("build-image") {
    dockerBuilder {
        gitRepoUrl = "git@github.com:wizeline/wz-statuspage.git"
        gitCredentialsId = 'b9abf261-0552-45f2-972d-08f3800d3d4f'
        gitSha  = "develop"

        dockerImageName = "cachet-backend"
        dockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
        slackChannelName = 'jenkins'

        dockerSourceRelativePath = "backend"
    }
  }


}
