//#!Groovy
def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "dockerRunner.groovy"
  print config

  if (!config.dockerImageName) {
    error 'You must provide a dockerImageName'
  }

  if (!config.dockerImageTag) {
    error 'You must provide a dockerImageTag'
  }

  if (!config.dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId'
  }

  def slackChannelName = config.slackChannelName ?: 'jenkins'
  def slackToken = config.slackToken
  def muteSlack = config.muteSlack ?: 'false'
  muteSlack = (muteSlack == 'true')

  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId ?: ''
  // For service discovery only
  def dockerDaemonUrl = config.dockerDaemonUrl ?: 'internal-docker-daemon-elb.wize.mx'
  def dockerRegistry = config.dockerRegistry ?: 'devops.wize.mx:5000'
  def dockerImageName = config.dockerImageName
  def dockerImageTag = config.dockerImageTag
  def dockerDaemonHost
  def dockerDaemonPort = config.dockerDaemonPort ?: '4243'
  def dockerDaemon

  node ('devops1'){

    stage('RunContainer'){

      withCredentials([[$class: 'UsernamePasswordMultiBinding',
                        credentialsId: dockerRegistryCredentialsId,
                        passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
                        usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {
        // Clean workspace before doing anything
        deleteDir()

        // Using a load balancer get the ip of a dockerdaemon and keep it for
        // future use.
        if (!dockerDaemonHost){
          dockerDaemonHost = sh(script: "dig +short ${dockerDaemonUrl} | head -n 1", returnStdout: true).trim()
        }
        dockerDaemon = "tcp://${dockerDaemonHost}:${dockerDaemonPort}"

        env.DOCKER_TLS_VERIFY = ""

        echo "Using remote docker daemon: ${dockerDaemon}"
        docker_bin="docker -H $dockerDaemon"

        sh "$docker_bin version"

        sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD devops.wize.mx:5000"

        // Call the buidler container
        exit_code = sh script: """
        $docker_bin rmi -f $dockerRegistry/$dockerImageName || true
        docker_id=\$($docker_bin create $dockerRegistry/$dockerImageName:$dockerImageTag)
        $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true

        [ ! -z "\$EXIT_CODE" ] && exit \$EXIT_CODE;
        exit 0
        """, returnStatus: true

        // Ensure every exited container has been removed
        sh script: """
        containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
        [ -n "\$containers" ] && $docker_bin rm -f \$containers && $docker_bin rmi -f $dockerRegistry/$dockerImageName || exit 0
        """, returnStatus: true

        if (exit_code != 0 && exit_code != 3){
          echo "FAILURE"
          currentBuild.result = 'FAILURE'
          if (config.slackChannelName && !muteSlack){
            slackSend channel:"#${slackChannelName}",
                      color:'danger',
                      message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getuser()}"
          }
          error("FAILURE - Run container returned non 0 exit code")
          return 1
        }

        echo "SUCCESS"
        currentBuild.result = 'SUCCESS'
        if (config.slackChannelName && !muteSlack){
          slackSend channel:"#${slackChannelName}",
                    color:'good',
                    message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *SUCCESS*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getuser()}"
        }
     }

   }}
}
