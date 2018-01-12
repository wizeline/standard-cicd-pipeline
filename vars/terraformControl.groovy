//#!Groovy

def callTerraform(_cmd, configs) {

  return dockerTerraformRunner {
      dockerDaemonUrl = configs.dockerDaemonUrl
      jenkinsNode = configs.jenkinsNode

      gitRepoUrl       = configs.gitRepoUrl
      gitCredentialsId = configs.gitCredentialsId
      gitSha           = configs.gitSha

      dockerRegistry              = "devops.wize.mx:5000"
      dockerImageName             = "wize-terraform"
      dockerImageTag              = "0.1.0"
      dockerRegistryCredentialsId = "d656f8b1-dcf6-4737-83c1-c9199fb02463"

      tfSourceRelativePath = configs.tfSourceRelativePath
      tfAwsAccessKeyID     = configs.tfAwsAccessKeyID
      tfAwsSecretAccessKey = configs.tfAwsSecretAccessKey
      tfAwsRegion          = configs.tfAwsRegion
      tfAwsBackendBucketName    configs.tfAwsBackendBucketName
      tfAwsBackendBucketRegion  configs.tfAwsBackendBucketRegion
      tfAwsBackendBucketKeyPath configs.tfAwsBackendBucketKeyPath

      tfCommand = _cmd
      tfVars = configs.tfVars
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

  tf_configs.jobGitRepoUrl = params.GIT_REPO_URL
  tf_configs.jobGitCredentialsId = params.GIT_CREDENTIALS_ID
  tf_configs.jobGitSha = params.GIT_SHA

  tf_configs.jobDockerDaemonHost = config.jobDockerDaemonHost
  tf_configs.jobJenkinsNode = config.jobJenkinsNode

  tf_configs.jobTfSourceRelativePath = config.jobTfSourceRelativePath ?: '.'
  tf_configs.jobTfAwsAccessKeyID = config.jobTfAwsAccessKeyID
  tf_configs.jobTfAwsSecretAccessKey = config.jobTfAwsSecretAccessKey
  tf_configs.jobTfAwsRegion = config.jobTfAwsRegion
  tf_configs.jobTfAwsBackendBucketName = config.jobTfAwsBackendBucketName
  tf_configs.jobTfAwsBackendBucketRegion = config.jobTfAwsBackendBucketRegion
  tf_configs.jobTfAwsBackendBucketKeyPath = config.jobTfAwsBackendBucketKeyPath
  tf_configs.jobTfVars = config.jobTfVars

  if (params.TERRAFORM_COMMAND == "PLAN_APPLY"){
    plan_apply(tf_configs)
    return
  }

  if (params.TERRAFORM_COMMAND == "PLAN_DESTROY"){
    plan_destroy(tf_configs)
    return
  }

}
