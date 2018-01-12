//#!Groovy

def callTerraform(_cmd, tf_configs) {

  return dockerTerraformRunner {
      dockerDaemonUrl = tf_configs.dockerDaemonUrl
      jenkinsNode = tf_configs.jenkinsNode

      gitRepoUrl       = tf_configs.gitRepoUrl
      gitCredentialsId = tf_configs.gitCredentialsId
      gitSha           = tf_configs.gitSha

      dockerRegistry              = "devops.wize.mx:5000"
      dockerImageName             = "wize-terraform"
      dockerImageTag              = "0.1.0"
      dockerRegistryCredentialsId = "d656f8b1-dcf6-4737-83c1-c9199fb02463"

      tfSourceRelativePath = tf_configs.tfSourceRelativePath

      tfAwsAccessKeyID     = tf_configs.tfAwsAccessKeyID
      tfAwsSecretAccessKey = tf_configs.tfAwsSecretAccessKey
      tfAwsRegion          = tf_configs.tfAwsRegion

      tfAwsBackendBucketName    = tf_configs.tfAwsBackendBucketName
      tfAwsBackendBucketRegion  = tf_configs.tfAwsBackendBucketRegion
      tfAwsBackendBucketKeyPath = tf_configs.tfAwsBackendBucketKeyPath

      tfCommand = _cmd
      tfVars = tf_configs.tfVars
  }
}

def plan_apply(tf_configs) {
  def exit_code = 1
  def apply = false

  echo "Plan/Apply - path"

  stage('Plan:'){
    exit_code = callTerraform("/plan.sh", tf_configs)

    if (exit_code == 0){
      echo "SUCCESS"
      currentBuild.result = 'SUCCESS'
      return
    }
    if (exit_code != 0 && exit_code != 2){
      echo "FAILURE"
      currentBuild.result = 'FAILURE'
      return
    }


    if (exit_code == 2){
      try {
        input message: 'Apply Plan?', ok: 'Apply'
        apply = true
      } catch (err) {
        apply = false
        currentBuild.result = 'UNSTABLE'
      }
    }
  }

  if (apply) {
    stage('Apply:'){
      exit_code = callTerraform("/apply.sh", tf_configs)

      if (exit_code == 0) {
        echo "SUCCESS"
        currentBuild.result = 'SUCCESS'
      } else {
        echo "FAILURE"
        currentBuild.result = 'FAILURE'
      }
    }
  }
}

def plan_destroy(tf_configs) {
  def exit_code = 1
  def apply = false

  echo "Plan/Destroy - path"

  stage('Plan Destroy:'){
    exit_code = callTerraform("/plan_destroy.sh", tf_configs)

    if (exit_code == 0){
      echo "SUCCESS"
      currentBuild.result = 'SUCCESS'
      return
    }
    if (exit_code != 0 && exit_code != 2){
      echo "FAILURE"
      currentBuild.result = 'FAILURE'
      return
    }
    if (exit_code == 2){
      try {
        input message: 'Destroy?', ok: 'Destroy'
        apply = true
      } catch (err) {
        apply = false
        currentBuild.result = 'UNSTABLE'
      }
    }
  }

  if (apply) {
    stage('Destroy:'){
      exit_code = callTerraform("/destroy.sh", tf_configs)

      if (exit_code == 0) {
        echo "SUCCESS"
        currentBuild.result = 'SUCCESS'
      } else {
        echo "FAILURE"
        currentBuild.result = 'FAILURE'
      }
    }
  }
}

def call(body) {

  def config = [:]
  def tf_configs = [:]
  def return_hash = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  print config

  def slackChannelName = config.slackChannelName ?: 'jenkins'
  def slackToken = config.slackToken
  def muteSlack = config.muteSlack ?: 'false'
  muteSlack = (muteSlack == 'true')

  tf_configs.gitRepoUrl = params.GIT_REPO_URL
  tf_configs.gitCredentialsId = params.GIT_CREDENTIALS_ID
  tf_configs.gitSha = params.GIT_SHA

  tf_configs.dockerDaemonUrl = config.jobDockerDaemonHost
  tf_configs.jenkinsNode = config.jobJenkinsNode

  tf_configs.tfSourceRelativePath = config.jobTfSourceRelativePath ?: '.'

  tf_configs.tfAwsAccessKeyID = config.jobTfAwsAccessKeyID
  tf_configs.tfAwsSecretAccessKey = config.jobTfAwsSecretAccessKey
  tf_configs.tfAwsRegion = config.jobTfAwsRegion

  tf_configs.tfAwsBackendBucketName = config.jobTfAwsBackendBucketName
  tf_configs.tfAwsBackendBucketRegion = config.jobTfAwsBackendBucketRegion
  tf_configs.tfAwsBackendBucketKeyPath = config.jobTfAwsBackendBucketKeyPath

  tf_configs.tfVars = config.jobTfVars

  if (params.TERRAFORM_COMMAND == "PLAN_APPLY"){
    plan_apply(tf_configs)
    return
  }

  if (params.TERRAFORM_COMMAND == "PLAN_DESTROY"){
    plan_destroy(tf_configs)
    return
  }

}
