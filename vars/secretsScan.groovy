import org.wizeline.SlackI
import org.wizeline.DefaultValues

def call(body) {

  def config = [:]
  def exit_code
  def slack_i

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "secretsScan.groovy"
  print config

  // Docker Daemon
  def jobDockerDaemonHost  = config.jobDockerDaemonHost ?: params.DOCKER_DAEMON_HOST
  def jobDockerDaemonDnsDiscovery  = params.DOCKER_DAEMON_DNS_DISCOVERY
  def jobDockerDaemonPort  = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort
  def jobJenkinsNode       = config.jenkinsNode ?: params.JENKINS_NODE

  def jobGitRepoUrl       = params.GIT_REPO_URL
  def jobGitCredentialsId = params.GIT_CREDENTIALS_ID
  def jobGitSha           = params.GIT_SHA

  config.disableSubmodules = config.disableSubmodules ?: DefaultValues.defaultDisableSubmodules
  def jobDisableSubmodules = (config.disableSubmodules == "true") ? "true" : "false"
  println "disableSubmodules: ${jobDisableSubmodules}"

  def jobDockerRegistry  = params.DOCKER_REGISTRY   ?: DefaultValues.defaultDockerRegistry
  def jobDockerImageName = params.DOCKER_IMAGE_NAME
  def jobDockerImageTag  = params.DOCKER_IMAGE_TAG
  def jobDockerRegistryCredentialsId = params.DOCKER_REG_CREDENTIALS_ID

  def commits_MaxDepth = params.COMMITS_MAX_DEPTH ?: '5'

  slack_i = new SlackI(
    this,
    params,
    env,
    config,
    getUser()
  )

  slack_i.send('good', "*START* secret-scan (secretsScan)")

  exit_code = dockerSlaveRunner {
    dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
    dockerDaemonHost = jobDockerDaemonHost
    dockerDaemonPort = jobDockerDaemonPort
    jenkinsNode = jobJenkinsNode

    disableSubmodules = jobDisableSubmodules // "true"

    gitRepoUrl = jobGitRepoUrl
    gitCredentialsId = jobGitCredentialsId
    gitSha = jobGitSha

    dockerRegistry = jobDockerRegistry
    dockerImageName = jobDockerImageName
    dockerImageTag = jobDockerImageTag
    dockerRegistryCredentialsId = jobDockerRegistryCredentialsId

    envsRegExp = ""
    dockerWorkspace = "/project"
    dockerCommand = "--max_depth $commits_MaxDepth"
    // dockerInit = ""
  }

  if (exit_code == 0) {
    slack_i.send('good', "*SUCCESS* No secrets found (secretsScan)")
    echo "SUCCESS"
    currentBuild.result = 'SUCCESS'
  } else {
    slack_i.send('danger', "*FAILURE* High entropy string found, please review (secretsScan)")
    echo "FAILURE"
    currentBuild.result = 'FAILURE'
  }

}
