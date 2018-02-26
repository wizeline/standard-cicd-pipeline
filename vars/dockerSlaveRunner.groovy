//#!Groovy
import org.wizeline.DefaultValues

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

  // Slack info
  def slackChannelName = config.slackChannelName ?: DefaultValues.defaultSlackChannelName
  def slackToken       = config.slackToken
  def muteSlack        = config.muteSlack ?: DefaultValues.defaultMuteSlack
  muteSlack = (muteSlack == 'true')

  // Git Info
  def gitRepoUrl       = config.gitRepoUrl
  def gitCredentialsId = config.gitCredentialsId ?: DefaultValues.defaultGitCredentialsId
  def gitSha           = config.gitSha           ?: DefaultValues.defaultGitSha
  def gitBranch

  def envsRegExp = config.envsRegExp ?: ""
  def dockerWorkspace = config.dockerWorkspace ?: "/"
  def dockerCommand = config.dockerCommand ?: ""
  // dockerInit

  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId ?: DefaultValues.defaultDockerRegistryCredentialsId
  def dockerRegistry  = config.dockerRegistry ?: DefaultValues.defaultDockerRegistry
  def dockerImageName = config.dockerImageName
  def dockerImageTag  = config.dockerImageTag

  // For service discovery only
  def dockerDaemonHost = config.dockerDaemonHost
  def dockerDaemonUrl  = config.dockerDaemonUrl  ?: DefaultValues.defaultDockerDaemonUrl
  def dockerDaemonPort = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort
  def dockerDaemon

  def jenkinsNode = config.jenkinsNode

  def jobDisableSubmodules = (config.disableSubmodules == "true") ? "true" : "false"
  println "disableSubmodules: ${jobDisableSubmodules}"

  node (jenkinsNode){
    try {
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

        echo "Branch: ${gitBranch}"
        echo "SHA: ${gitSha}"

        println config.dockerInit
        if (config.dockerInit) {
          sh """
          cat <<EOF > init.sh
${config.dockerInit}
EOF
chmod +x init.sh
"""

        }
      }

      stage('RunSlaveContainer'){

        withCredentials([
          [
            $class: 'UsernamePasswordMultiBinding',
            credentialsId: dockerRegistryCredentialsId,
            passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
            usernameVariable: 'DOCKER_REGISTRY_USERNAME'
          ]
        ]) {


          def workspace = pwd()

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

          // Call the runner container
          exit_code = sh script: """
          env | sort | grep -E \"$envsRegExp\" > .env

          $docker_bin rmi -f $dockerRegistry/$dockerImageName:$dockerImageTag || true
          docker_id=\$($docker_bin create --env-file .env $dockerRegistry/$dockerImageName:$dockerImageTag $dockerCommand)
          $docker_bin cp $workspace/. \$docker_id:$dockerWorkspace
          $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true
          rm .env

          [ -n "\$EXIT_CODE" ] && exit \$EXIT_CODE;
          exit 0
          """, returnStatus: true

          // Ensure every exited container has been removed
          // temporary disable
          // sh script: """
          // containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
          // [ -n "\$containers" ] && $docker_bin rm -f \$containers && $docker_bin rmi -f $dockerRegistry/$dockerImageName:$dockerImageTag || exit 0
          // """, returnStatus: true

          return exit_code
        }
      }
    } catch (err) {
      println err
      if (config.slackChannelName && !muteSlack){
        slackSend channel:"#${slackChannelName}",
                  color:'danger',
                  message:"Build (dockerRunner) of ${env.JOB_NAME} - ${env.BUILD_NUMBER} *FAILED*\n(${env.BUILD_URL})\ndockerImageName: ${dockerImageName},  dockerImageTag: ${dockerImageTag}\n*Build started by* : ${getUser()}"
      }
      throw err
    }
  }
}
