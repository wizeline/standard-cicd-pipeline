//#!Groovy
import org.wizeline.SlackI
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery

def is_force_build(){
  print "FORCE_BUILD: ${params.FORCE_BUILD}"
  return params.FORCE_BUILD
}

def call(body) {

  def config = [:]
  def return_hash = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "testExecutor.groovy"
  print config

  // Git
  def jobGitRepoUrl        = params.GIT_REPO_URL
  def jobGitCredentialsId  = params.GIT_CREDENTIALS_ID
  def jobGitSha            = params.GIT_SHA
  def jobDisableSubmodules = config.disableSubmodules ?: DefaultValues.defaultDisableSubmodules
  def jobGitBranch
  def jobGitShaCommit

  // Docker
  def jobDockerImageName             = params.DOCKER_IMAGE_NAME
  def jobDockerSourceRelativePath    = params.DOCKER_SOURCE_REL_PATH    ?: DefaultValues.defaultDockerSourceRelativePath
  def jobDockerRegistryCredentialsId = params.DOCKER_REG_CREDENTIALS_ID ?: DefaultValues.defaultDockerRegistryCredentialsId
  def jobDockerRegistry              = params.DOCKER_REGISTRY     ?: DefaultValues.defaultDockerRegistry
  def jobDockerDockerfile            = params.DOCKER_DOCKERFILE   ?: DefaultValues.defaultDockerDockerfile

  // Docker Daemon
  def jobDockerDaemonHost         = config.jobDockerDaemonHost ?: params.DOCKER_DAEMON_HOST
  def jobDockerDaemonDnsDiscovery = params.DOCKER_DAEMON_DNS_DISCOVERY
  def jobDockerDaemonPort         = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort

  // Slack
  def jobSlackChannelName = params.SLACK_CHANNEL_NAME

  // Jenkins
  def jobJenkinsNode      = config.jobJenkinsNode ?: params.JENKINS_NODE

  node {
    stage ('Checkout') {
      git_info = gitCheckout {
        branch = jobGitSha
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

  stage("tests-execution:"){
    def branchTag = jobGitSha.replace("/", "_").replace("origin", "")
    def noTagCheck = is_force_build() ? "true" : "false"

    dockerBuilder {
      gitRepoUrl        = jobGitRepoUrl
      gitCredentialsId  = jobGitCredentialsId
      gitSha            = jobGitShaCommit
      disableSubmodules = jobDisableSubmodules

      dockerImageName  = jobDockerImageName
      dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
      dockerRegistry   = jobDockerRegistry
      slackChannelName = jobSlackChannelName

      dockerEnvTag     = branchTag
      dockerDockerfile = jobDockerDockerfile
      dockerNoTagCheck = noTagCheck
      dockerSourceRelativePath = jobDockerSourceRelativePath

      // dockerDaemonDnsDiscovery vs dockerDaemonHost
      // dockerDaemonDnsDiscovery: will select a dockerd from a elb
      // dockerDaemonHost: uses specific dockerd
      dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
      dockerDaemonHost = jobDockerDaemonHost
      dockerDaemonPort = jobDockerDaemonPort
      jenkinsNode      = jobJenkinsNode
    }

    dockerRunner {
      dockerImageName  = jobDockerImageName
      dockerImageTag   = branchTag
      dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
      dockerRegistry   = jobDockerRegistry
      slackChannelName = jobSlackChannelName

      dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
      dockerDaemonHost = jobDockerDaemonHost
      dockerDaemonPort = jobDockerDaemonPort
      jenkinsNode      = jobJenkinsNode
    }

    return_hash["tests-execution"] = "success"
  }

  return return_hash
}
