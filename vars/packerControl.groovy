//#!Groovy
import org.wizeline.SlackI

def call(body) {

  def config = [:]
  def tf_configs = [:]
  def slack_i
  def return_hash = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  print config

  // steps, params, env, config, build_user
  slack_i = new SlackI(
    this,
    params,
    env,
    config,
    getuser()
  )

  def jobPackerAwsRegion = params.AWS_REGION
  def jobPackerAwsAccessCredentialsId = params.AWS_CREDENTIALS_ID
  def jobPackerVars = params.PACKER_VARS
  def jobPackerSourceRelativePath = params.PACKER_SOURCE_REL_PATH

  def jobGitSha = params.BRANCH
  def jobGitRepoUrl = params.GIT_REPO_URL
  def jobGitCredentialsId = params.GIT_CREDENTIALS_ID

  def jobDockerImageName = params.DOCKER_IMAGE_NAME
  def jobDockerImageTag = params.DOCKER_IMAGE_TAG

  def exit_code

  slack_i.send('good', "*START* Build (packerControl)")

  exit_code = dockerPackerRunner() {
    packerAwsRegion = jobPackerAwsRegion
    packerAwsAccessCredentialsId = jobPackerAwsAccessCredentialsId
    packerVars = jobPackerVars
    packerSourceRelativePath = jobPackerSourceRelativePath

    gitSha = jobGitSha
    gitRepoUrl = jobGitRepoUrl
    gitCredentialsId = jobGitCredentialsId

    dockerImageName = jobDockerImageName
    dockerImageTag = jobDockerImageTag
    dockerRegistryCredentialsId = "d656f8b1-dcf6-4737-83c1-c9199fb02463"

    dockerDaemonHost = "internal-docker.wize.mx"
  }

  if (exit_code == 0) {
    slack_i.send('good', "*SUCCESS* Build AMI (packerControl)")
    echo "SUCCESS"
    currentBuild.result = 'SUCCESS'
  } else {
    slack_i.send('danger', "*FAILURE* Build AMI (packerControl)")
    echo "FAILURE"
    currentBuild.result = 'FAILURE'
  }


}
