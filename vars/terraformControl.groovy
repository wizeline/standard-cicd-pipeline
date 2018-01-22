//#!Groovy
import org.wizeline.SlackI

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

      tfAwsAccessCredentialsId = tf_configs.tfAwsAccessCredentialsId
      tfAwsRegion          = tf_configs.tfAwsRegion

      tfAwsBackendBucketName    = tf_configs.tfAwsBackendBucketName
      tfAwsBackendBucketRegion  = tf_configs.tfAwsBackendBucketRegion
      tfAwsBackendBucketKeyPath = tf_configs.tfAwsBackendBucketKeyPath

      tfCommand = _cmd
      tfVars = tf_configs.tfVars
  }
}

def plan_apply(tf_configs, slack_i) {
  def exit_code = 1
  def apply = false

  echo "Plan/Apply - path"

  stage('Plan:'){
    exit_code = callTerraform("/plan.sh", tf_configs)

    if (exit_code == 0){
      slack_i.send('good', "*SUCCESS* Plan 0 - Succeeded, diff is empty (no changes) (terraformControl)")
      echo "SUCCESS"
      currentBuild.result = 'SUCCESS'
      return
    }
    if (exit_code != 0 && exit_code != 2){
      slack_i.send('danger', "*FAILURE* Plan 1 - Errored (terraformControl)")
      echo "FAILURE"
      currentBuild.result = 'FAILURE'
      return
    }


    if (exit_code == 2){
      slack_i.send('good', "Plan *Apply Awaiting Approval*. 2 - Succeeded, there is a diff (terraformControl)")
      try {
        input message: 'Apply Plan?', ok: 'Apply'
        apply = true
      } catch (err) {
        slack_i.send('warning', "*Plan Discarded* (terraformControl)")
        apply = false
        currentBuild.result = 'UNSTABLE'
      }
    }
  }

  if (apply) {
    stage('Apply:'){
      exit_code = callTerraform("/apply.sh", tf_configs)

      if (exit_code == 0) {
        slack_i.send('good', "*SUCCESS* Changes Applied (terraformControl)")
        echo "SUCCESS"
        currentBuild.result = 'SUCCESS'
      } else {
        slack_i.send('danger', "*FAILURE* Apply Failed (terraformControl)")
        echo "FAILURE"
        currentBuild.result = 'FAILURE'
      }
    }
  }
}

def plan_destroy(tf_configs, slack_i) {
  def exit_code = 1
  def apply = false

  echo "Plan/Destroy - path"

  stage('Plan Destroy:'){
    exit_code = callTerraform("/plan_destroy.sh", tf_configs)

    if (exit_code == 0){
      slack_i.send('good', "*SUCCESS* Plan 0 - Succeeded, diff is empty (no changes) (terraformControl)")
      echo "SUCCESS"
      currentBuild.result = 'SUCCESS'
      return
    }
    if (exit_code != 0 && exit_code != 2){
      slack_i.send('danger', "*FAILURE* Plan 1 - Errored (terraformControl)")
      echo "FAILURE"
      currentBuild.result = 'FAILURE'
      return
    }
    if (exit_code == 2){
      slack_i.send('good', "Plan *Destroy Awaiting Approval*. 2 - Succeeded, there is a diff (terraformControl)")
      try {
        input message: 'Destroy?', ok: 'Destroy'
        apply = true
      } catch (err) {
        slack_i.send('warning', "*Plan Discarded* (terraformControl)")
        apply = false
        currentBuild.result = 'UNSTABLE'
      }
    }
  }

  if (apply) {
    stage('Destroy:'){
      exit_code = callTerraform("/destroy.sh", tf_configs)

      if (exit_code == 0) {
        slack_i.send('good', "*SUCCESS* Destroy Applied (terraformControl)")
        echo "SUCCESS"
        currentBuild.result = 'SUCCESS'
      } else {
        slack_i.send('danger', "*FAILURE* Destroy Failed (terraformControl)")
        echo "FAILURE"
        currentBuild.result = 'FAILURE'
      }
    }
  }
}

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

  tf_configs.gitRepoUrl = params.GIT_REPO_URL
  tf_configs.gitCredentialsId = params.GIT_CREDENTIALS_ID
  tf_configs.gitSha = params.GIT_SHA

  tf_configs.dockerDaemonUrl = config.jobDockerDaemonHost
  tf_configs.jenkinsNode = config.jobJenkinsNode

  tf_configs.tfSourceRelativePath = config.jobTfSourceRelativePath ?: '.'

  tf_configs.tfAwsAccessCredentialsId = config.jobTfAwsAccessCredentialsId
  tf_configs.tfAwsRegion = config.jobTfAwsRegion

  tf_configs.tfAwsBackendBucketName = config.jobTfAwsBackendBucketName
  tf_configs.tfAwsBackendBucketRegion = config.jobTfAwsBackendBucketRegion
  tf_configs.tfAwsBackendBucketKeyPath = config.jobTfAwsBackendBucketKeyPath

  tf_configs.tfVars = config.jobTfVars

  slack_i.send('good', "*START* ${params.TERRAFORM_COMMAND} (terraformControl)")

  if (params.TERRAFORM_COMMAND == "PLAN_APPLY"){
    plan_apply(tf_configs, slack_i)
    return
  }

  if (params.TERRAFORM_COMMAND == "PLAN_DESTROY"){
    plan_destroy(tf_configs, slack_i)
    return
  }

}
