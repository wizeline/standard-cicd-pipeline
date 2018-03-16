//#!Groovy
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery

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

  if (!config.tfAwsAccessCredentialsId) {
    error 'You must provide a tfAwsAccessCredentialsId'
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

  // Terraform Info
  def tfSourceRelativePath = config.tfSourceRelativePath ?: '.'
  def tfCommand = config.tfCommand ?: '/plan.sh'
  def tfAwsAccessKeyID = config.tfAwsAccessKeyID
  def tfAwsSecretAccessKey = config.tfAwsSecretAccessKey
  def tfAwsAccessCredentialsId = config.tfAwsAccessCredentialsId
  def tfAwsRegion = config.tfAwsRegion
  def tfAwsBackendBucketName = config.tfAwsBackendBucketName
  def tfAwsBackendBucketRegion = config.tfAwsBackendBucketRegion
  def tfAwsBackendBucketKeyPath = config.tfAwsBackendBucketKeyPath

  // Docker Image Info
  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId ?: DefaultValues.defaultDockerRegistryCredentialsId
  def dockerRegistry  = config.dockerRegistry ?: DefaultValues.defaultDockerRegistry
  def dockerImageName = config.dockerImageName
  def dockerImageTag  = config.dockerImageTag

  // For service discovery only
  def dockerDaemonHost = config.dockerDaemonHost
  def dockerDaemonDnsDiscovery  = config.dockerDaemonDnsDiscovery  ?: DefaultValues.defaultdockerDaemonDnsDiscovery
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

        println config.tfVars
        if (config.tfVars) {
          sh """
          cat <<EOF > $tfSourceRelativePath/terraform.tfvars
          ${config.tfVars}
EOF"""
        }
      }

      stage('RunTerraformContainer'){

        withCredentials([
          [
            $class: 'UsernamePasswordMultiBinding',
            credentialsId: dockerRegistryCredentialsId,
            passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
            usernameVariable: 'DOCKER_REGISTRY_USERNAME'
          ],
          [
            $class: 'UsernamePasswordMultiBinding',
            credentialsId: tfAwsAccessCredentialsId,
            passwordVariable: 'AWS_TF_PASSWORD',
            usernameVariable: 'AWS_TF_USERNAME'
          ]
        ]) {

          def workspace = pwd()

          // Using a load balancer get the ip of a dockerdaemon and keep it for
          // future use.
          dockerDaemon = DockerdDiscovery.getDockerDaemon(this, dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery)

          env.DOCKER_TLS_VERIFY = ""

          env_vars = """DOCKER_TLS_VERIFY=""
AWS_ACCESS_KEY_ID=$AWS_TF_USERNAME
AWS_SECRET_ACCESS_KEY=$AWS_TF_PASSWORD
AWS_DEFAULT_REGION=$tfAwsRegion
AWS_TF_BACKEND_BUCKET=$tfAwsBackendBucketName
AWS_TF_BACKEND_REGION=$tfAwsBackendBucketRegion
AWS_TF_BACKEND_KEY_PATH=$tfAwsBackendBucketKeyPath
TF_SOURCE_RELATIVE_PATH=$tfSourceRelativePath
"""

          writeFile file: ".env", text: env_vars

          echo "Using remote docker daemon: ${dockerDaemon}"
          docker_bin="docker -H $dockerDaemon"

          sh "$docker_bin version"

          sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD $dockerRegistry"

          // Call the buidler container
          exit_code = sh script: """
          $docker_bin rmi -f $dockerRegistry/$dockerImageName:$dockerImageTag || true
          docker_id=\$($docker_bin create --env-file .env $dockerRegistry/$dockerImageName:$dockerImageTag $tfCommand)
          $docker_bin cp $workspace/. \$docker_id:/project
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
