//#!Groovy
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery
import org.wizeline.InfluxMetrics
import org.wizeline.BuildManifest

def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "dockerBuilder.groovy"
  print config

  // Validations
  if (!config.gitRepoUrl) {
    error 'You must provide a gitRepoUrl'
  }

  if (!config.gitCredentialsId) {
    error 'You must provide a gitCredentialsId'
  }

  if (!config.gitSha) {
    error 'You must provide a gitSha'
  }

  if (!config.dockerImageName) {
    error 'You must provide a dockerImageName'
  }

  if (!config.dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId'
  }

  // Slack Info
  def slackChannelName = config.slackChannelName ?: DefaultValues.defaultSlackChannelName
  def slackToken       = config.slackToken
  def muteSlack        = config.muteSlack ?: DefaultValues.defaultMuteSlack
  muteSlack = (muteSlack == 'true')

  // Git Info
  def gitRepoUrl       = config.gitRepoUrl
  def gitCredentialsId = config.gitCredentialsId ?: DefaultValues.defaultGitCredentialsId
  def gitSha           = config.gitSha           ?: DefaultValues.defaultGitSha
  def gitBranch

  // Docker Image Info
  def dockerImageName             = config.dockerImageName
  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId
  def dockerRegistry              = config.dockerRegistry           ?: DefaultValues.defaultDockerRegistry
  def dockerEnvTag                = config.dockerEnvTag             ?: DefaultValues.defaultDockerEnvTag
  def dockerEnvTags               = config.dockerEnvTags             ?: DefaultValues.defaultDockerEnvTags
  def dockerSourceRelativePath    = config.dockerSourceRelativePath ?: DefaultValues.defaultDockerSourceRelativePath
  def dockerDockerfileAbsolutePath = config.dockerDockerfileAbsolutePath ?: DefaultValues.defaultDockerDockerfileAbsolutePath
  def dockerDockerfile            = config.dockerDockerfile         ?: DefaultValues.defaultDockerDockerfile
  def dockerNoTagCheck            = config.dockerNoTagCheck         ?: DefaultValues.defaultDockerNoTagCheck

  // For service discovery only
  def dockerDaemonHost = config.dockerDaemonHost
  def dockerDaemonDnsDiscovery  = config.dockerDaemonDnsDiscovery  ?: DefaultValues.defaultdockerDaemonDnsDiscovery
  def dockerDaemonPort = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort
  def dockerDaemon

  def jenkinsNode = config.jenkinsNode

  def jobDisableSubmodules = (config.disableSubmodules == "true") ? "true" : "false"
  println "disableSubmodules: ${jobDisableSubmodules}"

  // BuildManifest
  def buildManifest

  // InfluxDB
  def influxdb = new InfluxMetrics(
    this,
    params,
    env,
    config,
    getUser(),
    "docker-builder",
    env.INFLUX_URL,
    env.INFLUX_API_AUTH
  )
  influxdb.sendInfluxPoint(influxdb.START)

  node (jenkinsNode){
    try{
      // Clean workspace before doing anything
      deleteDir()

      stage ('Checkout') {

        git_info = gitCheckout {
          branch = gitSha
          credentialsId = gitCredentialsId
          repoUrl = gitRepoUrl
          disableSubmodules = jobDisableSubmodules
        }
        gitBranch = git_info["git-branch"]
        gitSha = git_info["git-commit-sha"]

        buildManifest = new BuildManifest(
          this,
          params,
          env,
          config,
          getUser(),
          git_info,
          "docker-builder"
        )
        buildManifest.generate()

        echo "Branch: ${gitBranch}"
        echo "SHA: ${gitSha}"

        if (config.slackChannelName && !muteSlack){
          slackSend channel:"#${slackChannelName}",
                    color:'good',
                    message:"*START* Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER}\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* :${getUser()}"
        }
      }

     stage('DockerBuildRetagPush'){

         withCredentials([[$class: 'UsernamePasswordMultiBinding',
                         credentialsId: dockerRegistryCredentialsId,
                         passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
                         usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {

          def workspace = pwd()
          def job_as_service_image = "${DefaultValues.defaultJobsAsAServiceImage}:${DefaultValues.defaultJobsAsAServiceImageTag}"

          // Using a load balancer get the ip of a dockerdaemon and keep it for
          // future use.
          dockerDaemon = DockerdDiscovery.getDockerDaemon(this, dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery)

          def dockerCommitTag = dockerEnvTag

          // Feed the external build args
          def build_args = sh script: """
          env | grep -e '^DOCKER_ARG_' || true
          """, returnStdout: true

          env_vars = """DOCKER_REGISTRY=$dockerRegistry
DOCKER_IMAGE_NAME=$dockerImageName
DOCKER_DOCKERFILE_ABS_PATH=$dockerDockerfileAbsolutePath
DOCKER_DOCKERFILE=$dockerDockerfile
DOCKER_ENV_TAG=$dockerEnvTag
DOCKER_ENV_TAGS=$dockerEnvTags
NO_TAG_CHECK=$dockerNoTagCheck
DOCKER_COMMIT_TAG=$dockerCommitTag
DOCKER_TLS_VERIFY=""
DOCKER_DAEMON_URL=$dockerDaemon
DOCKER_REGISTRY_PASSWORD=$DOCKER_REGISTRY_PASSWORD
DOCKER_REGISTRY_USERNAME=$DOCKER_REGISTRY_USERNAME
$build_args
"""

          writeFile file: ".env", text: env_vars

          echo "Using remote docker daemon: ${dockerDaemon}"
          docker_bin="docker -H $dockerDaemon"
          env.DOCKER_TLS_VERIFY = ""

          sh "$docker_bin version"

          sh "echo \"$DOCKER_REGISTRY_PASSWORD\" | $docker_bin login -u $DOCKER_REGISTRY_USERNAME --password-stdin $dockerRegistry"

          // Call the buidler container
          exit_code = sh script: """
          set +e

          # env | sort | grep -E \"DOCKER|NO_TAG_CHECK\" > .env
          $docker_bin pull $job_as_service_image || true
          docker_id=\$($docker_bin create --env-file .env $job_as_service_image /build)
          $docker_bin cp $workspace/$dockerSourceRelativePath/. \$docker_id:/source
          $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true
          rm .env

          [ -n "\$EXIT_CODE" ] && exit \$EXIT_CODE;
          exit 0
          """, returnStatus: true

          // Ensure every exited container has been removed
          sh script: """
          containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
          [ -n "\$containers" ] && $docker_bin rm \$containers || exit 0
          """, returnStatus: true

          TAG_ALREADY_EXIST_CODE = 3
          if (exit_code != 0 && exit_code != TAG_ALREADY_EXIST_CODE){
            echo "FAILURE"
            currentBuild.result = 'FAILURE'
            if (config.slackChannelName && !muteSlack){
              slackSend channel:"#${slackChannelName}",
                        color:'danger',
                        message:"Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* : ${getUser()}"
            }
            error("FAILURE - Build container returned non 0 exit code")
            return 1
          }

          echo "SUCCESS"
          currentBuild.result = 'SUCCESS'
          if (config.slackChannelName && !muteSlack){
            slackSend channel:"#${slackChannelName}",
                      color:'good',
                      message:"Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER} *SUCCESS*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* : ${getUser()}"
          }
         }
       }

     influxdb.processBuildResult(currentBuild)

    } catch (err) {
      println err
      if (config.slackChannelName && !muteSlack){
        slackSend channel:"#${slackChannelName}",
                   color:'danger',
                   message:"Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* : ${getUser()}"
      }
      throw err
    }
  } // /node
}
