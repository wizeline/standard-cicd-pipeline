//#!Groovy

// # Calling script example
// // def jobK8sConfigCredentialsId = params.K8S_CREDENTIALS_ID
// // def jobK8sContext = params.K8S_CONTEXT
// // def jobK8sNamespace = params.K8S_NAMESPACE
// // def jobK8sDeployment = params.K8S_DEPLOYMENT
// // def jobK8sEnv = params.K8S_ENV
// // def jobDockerRegistry = params.DOCKER_REGISTRY
// // def jobDockerImageName = params.DOCKER_IMAGE_NAME
// // def jobDockerImageTag = params.DOCKER_COMMIT_TAG
// // def jobDockerDaemon = params.DOCKER_DAEMON_URL
// //
// // kubernetesDeployer {
// //   k8sConfigCredentialsId = jobK8sConfigCredentialsId
// //   k8sContext = jobK8sContext
// //   k8sNamespace = jobK8sNamespace
// //   k8sDeploymentName = jobK8sDeployment
// //   k8sEnvTag = jobK8sEnv
// //
// //   dockerRegistry = jobDockerRegistry
// //   dockerImageName = jobDockerImageName
// //   dockerImageTag = jobDockerImageTag
// //
// //   dockerDaemonHost = "internal-docker.wize.mx"
// //   dockerRegistryCredentialsId = "d656f8b1-dcf6-4737-83c1-c9199fb02463"
// // }


import org.wizeline.SlackI

def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "kubernetesDeployer.groovy"
  print config

  // steps, params, env, config, build_user
  slack_i = new SlackI(
    this,
    params,
    env,
    config,
    getuser()
  )
  slack_i.useK8sSufix()

  if (!config.dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId'
  }

  if (!config.k8sConfigCredentialsId) {
    error 'You must provide a k8sConfigCredentialsId'
  }

  if (!config.dockerImageName) {
    error 'You must provide a dockerImageName'
  }

  if (!config.dockerImageTag) {
    error 'You must provide a dockerImageTag'
  }

  if (!config.k8sContext) {
    error 'You must provide a k8sContext'
  }

  if (!config.k8sDeploymentName) {
    error 'You must provide a k8sDeploymentName'
  }

  if (!config.k8sEnvTag) {
    error 'You must provide a k8sEnvTag'
  }

  def k8sNamespace = config.k8sNamespace ?: 'default'

  def dockerRegistry = config.dockerRegistry ?: 'devops.wize.mx:5000'

  def dockerDaemonUrl = config.dockerDaemonUrl ?: 'internal-docker-daemon-elb.wize.mx'
  def dockerDaemonHost= config.dockerDaemonHost
  def dockerDaemonPort = config.dockerDaemonPort ?: '4243'
  def dockerDaemon
  def jenkinsNode = config.jenkinsNode

  slack_i.send("good", "kubernetesDeployer *START*")
  node (jenkinsNode){
    try{
      // Clean workspace before doing anything
      deleteDir()

      stage('CDPathDeployImageK8s'){

        withCredentials([
          [$class: 'FileBinding',
            credentialsId: config.k8sConfigCredentialsId, variable: 'K8S_CONFIG'],
          [$class: 'UsernamePasswordMultiBinding',
            credentialsId: config.dockerRegistryCredentialsId,
            passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
            usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {

            def job_as_service_image = "devops.wize.mx:5000/jobs-as-a-service"
            def workspace = pwd()

            // Using a load balancer get the ip of a dockerdaemon and keep it for
            // future use.
            if (!dockerDaemonHost){
              dockerDaemonHost = sh(script: "dig +short ${dockerDaemonUrl} | head -n 1", returnStdout: true).trim()
            }
            dockerDaemon = "tcp://${dockerDaemonHost}:${dockerDaemonPort}"


            sh "cp -f $K8S_CONFIG .K8S_CONFIG.yaml"

        env_vars = """
K8S_CONFIG=/root/.K8S_CONFIG.yaml
K8S_CONTEXT=$config.k8sContext
K8S_NAMESPACE=$k8sNamespace
K8S_DEPLOYMENT=$config.k8sDeploymentName
K8S_ENV=$config.k8sEnvTag
DOCKER_REGISTRY=$dockerRegistry
DOCKER_IMAGE_NAME=$config.dockerImageName
DOCKER_COMMIT_TAG=$config.dockerImageTag
DOCKER_DAEMON_URL=$dockerDaemon
DOCKER_REGISTRY_PASSWORD=$DOCKER_REGISTRY_PASSWORD
DOCKER_REGISTRY_USERNAME=$DOCKER_REGISTRY_USERNAME
"""

            writeFile file: ".env", text: env_vars

            echo "Using remote docker daemon: ${dockerDaemon}"
            docker_bin="docker -H $dockerDaemon"

            sh "$docker_bin version"

            sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD devops.wize.mx:5000"

            // Call the deployer container
            exit_code = sh script: """
            $docker_bin pull $job_as_service_image || true
            docker_id=\$($docker_bin create --env-file .env $job_as_service_image /deploy)
            $docker_bin cp .K8S_CONFIG.yaml \$docker_id:/root
            $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true
            rm .env

            [ ! -z "\$EXIT_CODE" ] && exit \$EXIT_CODE;
            exit 0
            """, returnStatus: true

            // Ensure every exited container has been removed
            sh script: """
            containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
            [ -n "\$containers" ] && $docker_bin rm \$containers || exit 0
            """, returnStatus: true

            if (exit_code != 0){
              echo "FAILURE"
              currentBuild.result = 'FAILURE'
              slack_i.send("danger", "kubernetesDeployer *FAILURE*")
              error("FAILURE - Build container returned non 0 exit code")
              return 1
            }

            echo "SUCCESS"
            currentBuild.result = 'SUCCESS'
            slack_i.send("good", "kubernetesDeployer *SUCCESS*")
          }
      }
    } catch (err) {
      println err
      currentBuild.result = 'FAILURE'
      slack_i.send("danger", "kubernetesDeployer *FAILED*")
      throw err
    }
  }
}
