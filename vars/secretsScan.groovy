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

  print config

  def jobDockerDaemonHost = config.dockerDaemonHost
  def jobJenkinsNode = config.jenkinsNode

  def jobGitRepoUrl       = params.GIT_REPO_URL
  def jobGitCredentialsId = params.GIT_CREDENTIALS_ID
  def jobGitSha           = params.GIT_SHA

  config.disableSubmodules = config.disableSubmodules ?: DefaultValues.defaultDisableSubmodules
  def jobDisableSubmodules = (config.disableSubmodules == "true") ? "true" : "false"
  println "disableSubmodules: ${jobDisableSubmodules}"

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
    dockerDaemonHost = jobDockerDaemonHost // "internal-docker.wize.mx"
    jenkinsNode = jobJenkinsNode // "devops1"
    disableSubmodules = jobDisableSubmodules // "true"

    gitRepoUrl = jobGitRepoUrl
    gitCredentialsId = jobGitCredentialsId
    gitSha = jobGitSha

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
