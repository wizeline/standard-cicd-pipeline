//#!Groovy
import org.wizeline.SlackI
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery

def is_main_branch(){
  return params.BRANCH == "origin/develop" ||
  params.BRANCH == "origin/stage" ||
  params.BRANCH == "origin/master" ||
  params.BRANCH == "develop" ||
  params.BRANCH == "stage" ||
  params.BRANCH == "master" ||
  params.BRANCH == "origin/development" ||
  params.BRANCH == "origin/staging" ||
  params.BRANCH == "development" ||
  params.BRANCH == "staging"
}

def is_force_build(){
  print "FORCE_BUILD: ${params.FORCE_BUILD}"
  return params.FORCE_BUILD
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

  // Git
  def jobGitRepoUrl        = params.GIT_REPO_URL
  def jobGitCredentialsId  = params.GIT_CREDENTIALS_ID
  def jobGitSha            = params.BRANCH
  def jobGitShaNoOrigin    = jobGitSha
  def jobDisableSubmodules = config.disableSubmodules ?: DefaultValues.defaultDisableSubmodules
  def jobGitBranch
  def jobGitShaCommit

  // Docker
  def jobDockerImageName             = params.DOCKER_IMAGE_NAME
  def jobDockerSourceRelativePath    = params.DOCKER_SOURCE_REL_PATH
  def jobDockerRegistryCredentialsId = params.DOCKER_REG_CREDENTIALS_ID ?: DefaultValues.defaultDockerRegistryCredentialsId
  def jobDockerRegistry              = params.DOCKER_REGISTRY     ?: DefaultValues.defaultDockerRegistry
  def jobDockerDockerfile            = params.DOCKER_DOCKERFILE   ?: DefaultValues.defaultDockerDockerfile
  def jobDockerNoTagCheck            = params.DOCKER_NO_TAG_CHECK ?: DefaultValues.defaultDockerNoTagCheck

  // Docker Daemon
  def jobDockerDaemonHost  = config.jobDockerDaemonHost ?: params.DOCKER_DAEMON_HOST
  def jobDockerDaemonDnsDiscovery  = params.DOCKER_DAEMON_DNS_DISCOVERY
  def jobDockerDaemonPort  = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort

  // Slack
  def jobSlackChannelName  = params.SLACK_CHANNEL_NAME

  def jobJenkinsNode       = config.jobJenkinsNode ?: params.JENKINS_NODE

  def disableLint = config.disableLint ?: 'false'
  def disableUnitTests = config.disableUnitTests ?: 'false'
  def disableBuildImage = config.disableImage ?: 'false'

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
        // def test_tag = "unit-test-${return_hash["git-sha"]}"
        def test_tag = "unit-test"
        dockerBuilder {
            gitRepoUrl = jobGitRepoUrl
            gitCredentialsId = jobGitCredentialsId
            gitSha  = jobGitShaCommit
            disableSubmodules = jobDisableSubmodules

            dockerImageName = jobDockerImageName
            dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
            dockerRegistry = jobDockerRegistry
            slackChannelName = jobSlackChannelName

            dockerEnvTag = test_tag
            dockerDockerfile = "Dockerfile.unit-tests"
            dockerNoTagCheck = "true"
            dockerSourceRelativePath = jobDockerSourceRelativePath

            // dockerDaemonDnsDiscovery vs dockerDaemonHost
            // dockerDaemonDnsDiscovery: will select a dockerd from a elb
            // dockerDaemonHost: uses specific dockerd
            dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
            dockerDaemonHost = jobDockerDaemonHost
            dockerDaemonPort = jobDockerDaemonPort
            jenkinsNode = jobJenkinsNode
        }

        dockerRunner {
          dockerImageName = jobDockerImageName
          dockerImageTag = test_tag
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          dockerRegistry = jobDockerRegistry
          slackChannelName = jobSlackChannelName

          dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
          dockerDaemonHost = jobDockerDaemonHost
          dockerDaemonPort = jobDockerDaemonPort
          jenkinsNode = jobJenkinsNode
        }
        return_hash["unit-tests"] = "success"
      } else {
        // mark stage as not done
        return_hash["unit-tests"] = "not-run"
        echo "UNSTABLE"
        currentBuild.result = 'SUCCESS'
      }
    }
  }

  tasks["lint"] = {
    stage("lint:"){
      if (disableLint != 'true'){
        // def lint_tag = "lint-${return_hash["git-sha"]}"
        def lint_tag = "lint"
        dockerBuilder {
            gitRepoUrl = jobGitRepoUrl
            gitCredentialsId = jobGitCredentialsId
            gitSha  = jobGitShaCommit
            disableSubmodules = jobDisableSubmodules

            dockerImageName = jobDockerImageName
            dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
            dockerRegistry = jobDockerRegistry
            slackChannelName = jobSlackChannelName

            dockerEnvTag = lint_tag
            dockerDockerfile = "Dockerfile.lint"
            dockerNoTagCheck = "true"
            dockerSourceRelativePath = jobDockerSourceRelativePath

            dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
            dockerDaemonHost = jobDockerDaemonHost
            dockerDaemonPort = jobDockerDaemonPort
            jenkinsNode = jobJenkinsNode
        }

        dockerRunner {
          dockerImageName = jobDockerImageName
          dockerImageTag = lint_tag
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          dockerRegistry = jobDockerRegistry
          slackChannelName = jobSlackChannelName

          dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
          dockerDaemonHost = jobDockerDaemonHost
          dockerDaemonPort = jobDockerDaemonPort
          jenkinsNode = jobJenkinsNode
        }
        return_hash["lint"] = "success"
      } else {
        // mark stage as not done
        return_hash["lint"] = "not-run"
        echo "UNSTABLE"
        currentBuild.result = 'SUCCESS'
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

  if ((disableBuildImage != "true") && (is_main_branch() || is_force_build())) {
    stage("build-image:") {
      def branchTag = jobGitShaNoOrigin.replace("/", "_").replace("origin", "")
      def noTagCheck = is_force_build() ? "true" : "false"
      dockerBuilder {
          gitRepoUrl        = jobGitRepoUrl
          gitCredentialsId  = jobGitCredentialsId
          gitSha            = jobGitShaCommit
          disableSubmodules = jobDisableSubmodules

          dockerImageName             = jobDockerImageName
          dockerEnvTag                = return_hash["git-sha"]
          dockerEnvTags               = "$branchTag"
          dockerRegistryCredentialsId = jobDockerRegistryCredentialsId
          dockerRegistry              = jobDockerRegistry
          dockerNoTagCheck            = jobDockerNoTagCheck
          slackChannelName            = jobSlackChannelName

          dockerSourceRelativePath = jobDockerSourceRelativePath
          dockerDockerfile         = jobDockerDockerfile
          dockerNoTagCheck         = noTagCheck

          dockerDaemonDnsDiscovery = jobDockerDaemonDnsDiscovery
          dockerDaemonHost = jobDockerDaemonHost
          dockerDaemonPort = jobDockerDaemonPort
          jenkinsNode      = jobJenkinsNode
      }
      return_hash["build-image"] = "success"
    }
  }

  return return_hash

}
