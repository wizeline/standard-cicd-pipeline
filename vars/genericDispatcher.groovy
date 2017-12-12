//#!Groovy
def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  print config

  def gitRepoUrl = params.GIT_REPO_URL
  def gitCredentialsId = params.GIT_CREDENTIALS_ID
  def gitSha = params.BRANCH

  deleteDir()

  stage ('Checkout') {
    git branch: gitSha, url: gitRepoUrl, credentialsId: gitCredentialsId
    gitBranch = sh(returnStdout:true, script:'git rev-parse --abbrev-ref HEAD').trim()
    gitSha = sh(returnStdout:true, script:'git rev-parse HEAD').trim()

    echo "Branch: ${gitBranch}"
    echo "SHA: ${gitSha}"

    if (config.slackChannelName){
      slackSend channel:"#${slackChannelName}",
                color:'good',
                message:"*START* Build of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER}\n(${env.BUILD_URL})\n *Build started by* :${getuser()}"
    }
  }

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
