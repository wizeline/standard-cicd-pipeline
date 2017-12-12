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

  if (!config.dockerImageName) {
    error 'You must provide a dockerImageName'
  }

  if (!config.dockerImageTag) {
    error 'You must provide a dockerImageTag'
  }

  if (!config.dockerRegistryCredentialsId) {
    error 'You must provide a dockerRegistryCredentialsId'
  }

  def dockerRegistryCredentialsId = config.dockerRegistryCredentialsId ?: ''
  def dockerDaemonUrl = config.dockerDaemonUrl ?: 'tcp://internal-docker.wize.mx:4243'
  def dockerRegistry = config.dockerRegistry ?: 'devops.wize.mx:5000'
  def dockerImageName = config.dockerImageName
  def dockerImageTag = config.dockerImageTag

  node ('devops1'){

    stage('RunContainer'){

      withCredentials([[$class: 'UsernamePasswordMultiBinding',
                        credentialsId: dockerRegistryCredentialsId,
                        passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
                        usernameVariable: 'DOCKER_REGISTRY_USERNAME']]) {
        // Clean workspace before doing anything
        deleteDir()

        env.DOCKER_TLS_VERIFY = ""

        echo "Using remote docker daemon: ${dockerDaemonUrl}"
        docker_bin="docker -H $dockerDaemonUrl"

        sh "$docker_bin version"

        sh "$docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD devops.wize.mx:5000"

        // Call the buidler container
        exit_code = sh script: """
        $docker_bin rmi -f $dockerRegistry/$dockerImageName || true
        docker_id=\$($docker_bin create $dockerRegistry/$dockerImageName:$dockerImageTag)
        $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true

        [ ! -z "\$EXIT_CODE" ] && exit \$EXIT_CODE;
        """, returnStatus: true

        // Ensure every exited container has been removed
        sh script: """
        containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
        [ -n "\$containers" ] && $docker_bin rm -f \$containers && $docker_bin rmi -f $dockerRegistry/$dockerImageName || exit 0
        """, returnStatus: true

        if (exit_code != 0 && exit_code != 3){
          currentBuild.result = 'FAILURE'
          return
        }

        currentBuild.result = 'SUCCESS'
     }

   }}
}
