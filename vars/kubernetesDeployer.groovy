//#!Groovy
import org.wizeline.SlackI
import org.wizeline.DefaultValues
import org.wizeline.DockerdDiscovery
import org.wizeline.InfluxMetrics

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
    getUser()
  )
  slack_i.useK8sSufix()

  def k8sNamespace      = params.K8S_NAMESPACE ?: DefaultValues.defaultK8sNamespace
  def k8sContext        = params.K8S_CONTEXT
  def k8sDeploymentName = params.K8S_DEPLOYMENT_NAME
  def k8sEnvTag         = params.K8S_ENV_TAG
  def k8sConfigCredentialsId = params.K8S_CONFIG_CREDENTIALS_ID

  // Docker
  def dockerImageName = params.DOCKER_IMAGE_NAME
  def dockerImageTag  = params.DOCKER_IMAGE_TAG
  def dockerRegistryCredentialsId = params.DOCKER_REG_CREDENTIALS_ID ?: DefaultValues.defaultDockerRegistryCredentialsId
  def dockerRegistry  = params.DOCKER_REGISTRY   ?: DefaultValues.defaultDockerRegistry

  // Docker Daemon
  def dockerDaemonHost  = config.dockerDaemonHost ?: params.DOCKER_DAEMON_HOST
  def dockerDaemonDnsDiscovery  = params.DOCKER_DAEMON_DNS_DISCOVERY
  def dockerDaemonPort  = config.dockerDaemonPort ?: DefaultValues.defaultDockerDaemonPort
  def dockerDaemon

  def jenkinsNode   = config.jobJenkinsNode ?: params.JENKINS_NODE

  // Validations
  if (!dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId (DOCKER_REG_CREDENTIALS_ID)'
  }

  if (!k8sConfigCredentialsId) {
    error 'You must provide a k8sConfigCredentialsId (K8S_CONFIG_CREDENTIALS_ID)'
  }

  if (!dockerImageName) {
    error 'You must provide a dockerImageName (DOCKER_IMAGE_NAME)'
  }

  if (!dockerImageTag) {
    error 'You must provide a dockerImageTag (DOCKER_IMAGE_TAG)'
  }

  if (!k8sContext) {
    error 'You must provide a k8sContext (K8S_CONTEXT)'
  }

  if (!k8sDeploymentName) {
    error 'You must provide a k8sDeploymentName (K8S_DEPLOYMENT_NAME)'
  }

  if (!k8sEnvTag) {
    error 'You must provide a k8sEnvTag (K8S_ENV_TAG)'
  }

  slack_i.send("good", "kubernetesDeployer *START*")
  // InfluxDB
  def influxdb = new InfluxMetrics(
    this,
    params,
    env,
    config,
    getUser(),
    "kubernetes-deployer",
    env.INFLUX_URL,
    env.INFLUX_API_AUTH
  )
  influxdb.sendInfluxPoint(influxdb.START)

  node (jenkinsNode){
    try{
      // Clean workspace before doing anything
      deleteDir()

      stage('CDPathDeployImageK8s'){

        withCredentials([
          [$class: 'FileBinding',
            credentialsId: k8sConfigCredentialsId, variable: 'K8S_CONFIG'],
          [$class: 'UsernamePasswordMultiBinding',
            credentialsId: dockerRegistryCredentialsId,
            passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
            usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {

            def job_as_service_image = DefaultValues.defaultJobsAsAServiceImage
            def workspace = pwd()

            // Using a load balancer get the ip of a dockerdaemon and keep it for
            // future use.
            dockerDaemon = DockerdDiscovery.getDockerDaemon(this, dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery)


            sh "cp -f $K8S_CONFIG .K8S_CONFIG.yaml"

        env_vars = """
K8S_CONFIG=/root/.K8S_CONFIG.yaml
K8S_CONTEXT=$k8sContext
K8S_NAMESPACE=$k8sNamespace
K8S_DEPLOYMENT=$k8sDeploymentName
K8S_ENV=$k8sEnvTag
DOCKER_REGISTRY=$dockerRegistry
DOCKER_IMAGE_NAME=$dockerImageName
DOCKER_COMMIT_TAG=$dockerImageTag
DOCKER_DAEMON_URL=$dockerDaemon
DOCKER_REGISTRY_PASSWORD=$DOCKER_REGISTRY_PASSWORD
DOCKER_REGISTRY_USERNAME=$DOCKER_REGISTRY_USERNAME
"""

            writeFile file: ".env", text: env_vars

            echo "Using remote docker daemon: ${dockerDaemon}"
            docker_bin="docker -H $dockerDaemon"

            sh "$docker_bin version"

            sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD $dockerRegistry"

            // Call the deployer container
            exit_code = sh script: """
            $docker_bin pull $job_as_service_image || true
            docker_id=\$($docker_bin create --env-file .env $job_as_service_image /deploy)
            $docker_bin cp .K8S_CONFIG.yaml \$docker_id:/root
            $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true
            rm .env

            [ -n "\$EXIT_CODE" ] && exit \$EXIT_CODE;
            exit 0
            """, returnStatus: true

            // Ensure every exited container has been removed
            sh script: """
            containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
            [ -n "\$containers" ] && $docker_bin rm \$containers || exit 0
            """, returnStatus: true

            if (exit_code != 0){
              echo "FAILURE"
              // error will trigger catch and slack and influx will be sent.
              error("FAILURE - Build container returned non 0 exit code")
              return 1
            }

            echo "SUCCESS"
            currentBuild.result = 'SUCCESS'
            slack_i.send("good", "kubernetesDeployer *SUCCESS*")
          }
      }

      influxdb.processBuildResult(currentBuild)

    } catch (err) {
      println err
      currentBuild.result = 'FAILURE'
      slack_i.send("danger", "kubernetesDeployer *FAILED*")
      throw err
    }
  }
}
