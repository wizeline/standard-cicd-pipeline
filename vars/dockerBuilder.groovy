//#!Groovy
def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "dockerBuilder.groovy"
  print config

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

  def slackChannelName = config.slackChannelName ?: 'jenkins'
  def slackToken = config.slackToken
  def muteSlack = config.muteSlack ?: 'false'
  muteSlack = (muteSlack == 'true')

  def gitRepoUrl = config.gitRepoUrl
  def gitCredentialsId = config.gitCredentialsId
  def gitSha = config.gitSha

  def dockerImageName = config.dockerImageName
  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId

  def gitBranch

  def dockerRegistry = config.dockerRegistry ?: 'devops.wize.mx:5000'
  def dockerEnvTag = config.dockerEnvTag ?: 'latest'
  def dockerSourceRelativePath = config.dockerSourceRelativePath ?: '.'
  // For service discovery only
  def dockerDaemonUrl = config.dockerDaemonUrl ?: 'internal-docker-daemon-elb.wize.mx'
  def dockerDockerfileAbsolutePath = config.dockerDockerfileAbsolutePath ?: '/source'
  def dockerDockerfile = config.dockerDockerfile ?: 'Dockerfile'
  def dockerNoTagCheck = config.dockerNoTagCheck ?: 'false'
  def dockerDaemonHost= config.dockerDaemonHost
  def dockerDaemonPort = config.dockerDaemonPort ?: '4243'
  def dockerDaemon

  def jenkinsNode = config.jenkinsNode



  node (jenkinsNode){
    try{
      // Clean workspace before doing anything
      deleteDir()

      stage ('Checkout') {
        // git branch: gitSha, url: gitRepoUrl, credentialsId: gitCredentialsId
        // gitBranch = sh(returnStdout:true, script:'git rev-parse --abbrev-ref HEAD').trim()
        // gitSha = sh(returnStdout:true, script:'git rev-parse HEAD').trim()

        git_info = gitCheckout {
          branch = gitSha
          credentialsId = gitCredentialsId
          repoUrl = gitRepoUrl
        }
        gitBranch = git_info["git-branch"]
        gitSha = git_info["git-commit-sha"]

        echo "Branch: ${gitBranch}"
        echo "SHA: ${gitSha}"

        if (config.slackChannelName && !muteSlack){
          slackSend channel:"#${slackChannelName}",
                    color:'good',
                    message:"*START* Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER}\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* :${getuser()}"
        }
      }

     stage('DockerBuildRetagPush'){

         withCredentials([[$class: 'UsernamePasswordMultiBinding',
                         credentialsId: dockerRegistryCredentialsId,
                         passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
                         usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {

          def workspace = pwd()

          // Using a load balancer get the ip of a dockerdaemon and keep it for
          // future use.
          if (!dockerDaemonHost){
            dockerDaemonHost = sh(script: "dig +short ${dockerDaemonUrl} | head -n 1", returnStdout: true).trim()
          }
          dockerDaemon = "tcp://${dockerDaemonHost}:${dockerDaemonPort}"

          env.DOCKER_REGISTRY = dockerRegistry
          env.DOCKER_IMAGE_NAME = dockerImageName
          env.DOCKER_DOCKERFILE_ABS_PATH = dockerDockerfileAbsolutePath
          env.DOCKER_DOCKERFILE = dockerDockerfile
          env.DOCKER_ENV_TAG = dockerEnvTag
          env.NO_TAG_CHECK = dockerNoTagCheck
          env.DOCKER_COMMIT_TAG = (dockerNoTagCheck == "true") ? dockerEnvTag : gitSha
          env.DOCKER_TLS_VERIFY = ""
          env.DOCKER_DAEMON_URL = dockerDaemon

          echo "Using remote docker daemon: ${dockerDaemon}"
          docker_bin="docker -H $dockerDaemon"

          sh "$docker_bin version"

          sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD devops.wize.mx:5000"

          // Call the buidler container
          exit_code = sh script: """
          env | sort | grep -E \"DOCKER|NO_TAG_CHECK\" > .env
          $docker_bin rmi -f devops.wize.mx:5000/jobs-as-a-service || true
          docker_id=\$($docker_bin create --env-file .env devops.wize.mx:5000/jobs-as-a-service /build)
          $docker_bin cp $workspace/$dockerSourceRelativePath/. \$docker_id:/source
          $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true
          rm .env

          [ ! -z "\$EXIT_CODE" ] && exit \$EXIT_CODE;
          exit 0
          """, returnStatus: true

          // Ensure every exited container has been removed
          sh script: """
          containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
          [ -n "\$containers" ] && $docker_bin rm -f \$containers && $docker_bin rmi -f devops.wize.mx:5000/jobs-as-a-service || exit 0
          """, returnStatus: true

          if (exit_code != 0 && exit_code != 3){
            echo "FAILURE"
            currentBuild.result = 'FAILURE'
            if (config.slackChannelName && !muteSlack){
              slackSend channel:"#${slackChannelName}",
                        color:'danger',
                        message:"Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* : ${getuser()}"
            }
            error("FAILURE - Build container returned non 0 exit code")
            return 1
          }

          echo "SUCCESS"
          currentBuild.result = 'SUCCESS'
          if (config.slackChannelName && !muteSlack){
            slackSend channel:"#${slackChannelName}",
                      color:'good',
                      message:"Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER} *SUCCESS*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* : ${getuser()}"
          }
         }
     }
     } catch (err) {
       println err
       if (config.slackChannelName && !muteSlack){
         slackSend channel:"#${slackChannelName}",
                   color:'danger',
                   message:"Build (dockerBuilder) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* : ${getuser()}"
       }
       throw err
     }

   }


}
