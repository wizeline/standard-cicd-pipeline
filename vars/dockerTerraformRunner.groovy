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

  if (!config.dockerImageTag) {
    error 'You must provide a dockerImageTag'
  }

  if (!config.dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId'
  }

  if (!config.tfAwsAccessKeyID) {
    error 'You must provide a tfAwsAccessKeyID'
  }

  if (!config.tfAwsSecretAccessKey) {
    error 'You must provide a tfAwsSecretAccessKey'
  }

  if (!config.tfAwsRegion) {
    error 'You must provide a tfAwsRegion'
  }

  if (!config.tfAwsBackendBucketName) {
    error 'You must provide a tfAwsBackendBucketName'
  }

  if (!config.tfAwsBackendBucketRegion) {
    error 'You must provide a tfAwsBackendBucketRegion'
  }

  if (!config.tfAwsBackendBucketKeyPath) {
    error 'You must provide a tfAwsBackendBucketKeyPath'
  }

  def slackChannelName = config.slackChannelName ?: 'jenkins'
  def slackToken = config.slackToken
  def muteSlack = config.muteSlack ?: 'false'
  muteSlack = (muteSlack == 'true')

  def gitRepoUrl = config.gitRepoUrl
  def gitCredentialsId = config.gitCredentialsId
  def gitSha = config.gitSha
  def gitBranch

  def tfSourceRelativePath = config.tfSourceRelativePath ?: '.'
  def tfCommand = config.tfCommand ?: '/plan.sh'
  def tfAwsAccessKeyID = config.tfAwsAccessKeyID
  def tfAwsSecretAccessKey = config.tfAwsSecretAccessKey
  def tfAwsRegion = config.tfAwsRegion
  def tfAwsBackendBucketName = config.tfAwsBackendBucketName
  def tfAwsBackendBucketRegion = config.tfAwsBackendBucketRegion
  def tfAwsBackendBucketKeyPath = config.tfAwsBackendBucketKeyPath

  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId ?: ''
  // For service discovery only
  def dockerDaemonUrl = config.dockerDaemonUrl ?: 'internal-docker-daemon-elb.wize.mx'
  def dockerRegistry = config.dockerRegistry ?: 'devops.wize.mx:5000'
  def dockerImageName = config.dockerImageName
  def dockerImageTag = config.dockerImageTag
  def dockerDaemonHost = config.dockerDaemonHost
  def dockerDaemonPort = config.dockerDaemonPort ?: '4243'
  def dockerDaemon

  def jenkinsNode = config.jenkinsNode

  node (jenkinsNode){
    try {
      // Clean workspace before doing anything
      deleteDir()

      stage ('Checkout') {
        git_info = gitCheckout {
          branch = gitSha
          credentialsId = gitCredentialsId
          repoUrl = gitRepoUrl
        }
        gitBranch = git_info["git-branch"]
        gitSha = git_info["git-commit-sha"]

        echo "Branch: ${gitBranch}"
        echo "SHA: ${gitSha}"

        println config.tfClosure
        if (config.tfClosure) {
          echo "tfClosure"
          config.tfClosure()
        }

        if (config.slackChannelName && !muteSlack){
          slackSend channel:"#${slackChannelName}",
                    color:'good',
                    message:"*START* (dockerTerraformRunner) of ${gitSha}:${env.JOB_NAME} - ${env.BUILD_NUMBER}\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerEnvTag: ${dockerEnvTag}\n*Build started by* :${getuser()}"
        }
      }

      stage('RunTerraformContainer'){

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

          env.DOCKER_TLS_VERIFY = ""

          env.AWS_ACCESS_KEY_ID = tfAwsAccessKeyID
          env.AWS_SECRET_ACCESS_KEY = tfAwsSecretAccessKey
          env.AWS_DEFAULT_REGION = tfAwsRegion
          env.AWS_TF_BACKEND_BUCKET = tfAwsBackendBucketName
          env.AWS_TF_BACKEND_REGION = tfAwsBackendBucketRegion
          env.AWS_TF_BACKEND_KEY_PATH = tfAwsBackendBucketKeyPath

          echo "Using remote docker daemon: ${dockerDaemon}"
          docker_bin="docker -H $dockerDaemon"

          sh "$docker_bin version"

          sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD devops.wize.mx:5000"

          // Call the buidler container
          exit_code = sh script: """
          env | sort | grep -E \"AWS_\" > .env
          $docker_bin rmi -f $dockerRegistry/$dockerImageName:$dockerImageTag || true
          docker_id=\$($docker_bin create --env-file .env $dockerRegistry/$dockerImageName:$dockerImageTag $tfCommand)
          $docker_bin cp $workspace/$tfSourceRelativePath/. \$docker_id:/project
          $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true
          rm .env

          [ ! -z "\$EXIT_CODE" ] && exit \$EXIT_CODE;
          exit 0
          """, returnStatus: true

          // Ensure every exited container has been removed
          sh script: """
          containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
          [ -n "\$containers" ] && $docker_bin rm -f \$containers && $docker_bin rmi -f $dockerRegistry/$dockerImageName:$dockerImageTag || exit 0
          """, returnStatus: true

          if (exit_code != 0){
            echo "FAILURE"
            currentBuild.result = 'FAILURE'
            if (config.slackChannelName && !muteSlack){
              slackSend channel:"#${slackChannelName}",
                        color:'danger',
                        message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getuser()}"
            }
            error("FAILURE - Run container returned non 0 exit code")
            return exit_code
          }

          echo "SUCCESS"
          currentBuild.result = 'SUCCESS'
          if (config.slackChannelName && !muteSlack){
            slackSend channel:"#${slackChannelName}",
                      color:'good',
                      message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *SUCCESS*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName}, dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getuser()}"
          }
        }
        return exit_code
      }
    } catch (err) {
      println err
      if (config.slackChannelName && !muteSlack){
        slackSend channel:"#${slackChannelName}",
                  color:'danger',
                  message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName},  dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getuser()}"
      }
      throw err
    }
  }
}
