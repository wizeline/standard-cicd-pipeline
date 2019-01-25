//#!Groovy
import org.wizeline.SlackI
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery
import org.wizeline.InfluxMetrics

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

  slack_i = new SlackI(
    this,
    params,
    env,
    config,
    getUser()
  )
  slack_i.useTestsSufix()

  // Jenkins
  def jobJenkinsNode      = config.jobJenkinsNode ?: params.JENKINS_NODE

  slack_i.send("good", "testExecutor *START*")
  // InfluxDB
  def influxdb = new InfluxMetrics(
    this,
    params,
    env,
    config,
    getUser(),
    "test-flow",
    env.INFLUX_URL,
    env.INFLUX_API_AUTH
  )
  influxdb.sendInfluxPoint(influxdb.START)

  def exit_code

  try{
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

        exit_code = dockerRunner {
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

      if (exit_code == 3){
        echo "UNSTABLE"
        currentBuild.result = 'UNSTABLE'
        slack_i.send("warning", "testExecutor *UNSTABLE*")
      } else {
        slack_i.send("good", "testExecutor *SUCCESS*")
      }

      influxdb.processBuildResult(currentBuild)

      return return_hash
    } // /node
  } catch (err) {
    println err
    currentBuild.result = 'FAILURE'
    slack_i.send("danger", "testExecutor *FAILED*")
    influxdb.processBuildResult(currentBuild)
    throw err
  }
}
