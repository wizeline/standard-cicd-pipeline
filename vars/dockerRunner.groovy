//#!Groovy
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery
import org.wizeline.InfluxMetrics

def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "dockerRunner.groovy"
  print config

  // Validations
  if (!config.dockerImageName) {
    error 'You must provide a dockerImageName'
  }

  if (!config.dockerImageTag) {
    error 'You must provide a dockerImageTag'
  }

  if (!config.dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId'
  }

  // Slack info
  def slackChannelName = config.slackChannelName ?: DefaultValues.defaultSlackChannelName
  def slackToken       = config.slackToken
  def muteSlack        = config.muteSlack ?: DefaultValues.defaultMuteSlack
  muteSlack = (muteSlack == 'true')

  // For service discovery only
  def dockerDaemonHost = config.dockerDaemonHost
  def dockerDaemonDnsDiscovery  = config.dockerDaemonDnsDiscovery  ?: DefaultValues.defaultdockerDaemonDnsDiscovery
  def dockerDaemonPort = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort
  def dockerDaemon

  // Image Info
  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId ?: DefaultValues.defaultDockerRegistryCredentialsId
  def dockerRegistry   = config.dockerRegistry   ?: DefaultValues.defaultDockerRegistry
  def dockerImageName  = config.dockerImageName
  def dockerImageTag   = config.dockerImageTag

  def jenkinsNode = config.jenkinsNode

  // InfluxDB
  def influxdb = new InfluxMetrics(
    this,
    params,
    env,
    config,
    getUser(),
    "docker-runner",
    env.INFLUX_URL,
    env.INFLUX_API_AUTH
  )
  influxdb.sendInfluxPoint(influxdb.START)

  node (jenkinsNode){
    try {

      stage('RunContainer'){

        withCredentials([[$class: 'UsernamePasswordMultiBinding',
                          credentialsId: dockerRegistryCredentialsId,
                          passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
                          usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {
          // Clean workspace before doing anything
          deleteDir()

          // Using a load balancer get the ip of a dockerdaemon and keep it for
          // future use.
          dockerDaemon = DockerdDiscovery.getDockerDaemon(this, dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery)

          env.DOCKER_TLS_VERIFY = ""

          echo "Using remote docker daemon: ${dockerDaemon}"
          docker_bin="docker -H $dockerDaemon"
          env.DOCKER_TLS_VERIFY = ""

          sh "$docker_bin version"

          sh "echo \"$DOCKER_REGISTRY_PASSWORD\" | $docker_bin login -u $DOCKER_REGISTRY_USERNAME --password-stdin $dockerRegistry"


          // Feed the external environment vars
          def env_file = sh script: """
          env | grep -e '^DOCKER_ENV_' || printf 'DOCKER_ENV_NULL=true'

          """, returnStdout: true

          // Create environment file for docker
          env_file = env_file.replaceAll("DOCKER_ENV_", "")
          writeFile file: "docker_env", text: env_file

          // Call the runner container
          exit_code = sh script: """
          set -e

          $docker_bin pull $dockerRegistry/$dockerImageName:$dockerImageTag || true
          docker_id=\$($docker_bin create --env-file docker_env $dockerRegistry/$dockerImageName:$dockerImageTag)
          $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true

          rm -f docker_env

          [ -n "\$EXIT_CODE" ] && exit \$EXIT_CODE;
          exit 0
          """, returnStatus: true

          // Ensure every exited container has been removed
          sh script: """
          containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
          [ -n "\$containers" ] && $docker_bin rm \$containers || exit 0
          """, returnStatus: true

          if (exit_code != 0 && exit_code != 3){
            echo "FAILURE"
            currentBuild.result = 'FAILURE'
            if (config.slackChannelName && !muteSlack){
              slackSend channel:"#${slackChannelName}",
                        color:'danger',
                        message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getUser()}"
            }
            influxdb.processBuildResult(currentBuild)
            error("FAILURE - Run container returned non 0 exit code")
            return 1
          }

          echo "SUCCESS"
          currentBuild.result = 'SUCCESS'
          if (config.slackChannelName && !muteSlack){
            slackSend channel:"#${slackChannelName}",
                      color:'good',
                      message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *SUCCESS*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getUser()}"
          }
        }
      } // /stage('RunContainer')

      influxdb.processBuildResult(currentBuild)
      return 0

    } catch (err) {
      println err
      if (config.slackChannelName && !muteSlack){
        slackSend channel:"#${slackChannelName}",
                  color:'danger',
                  message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName},  dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getUser()}"
      }
      influxdb.processBuildResult(currentBuild)
      throw err
    }
  }
}
