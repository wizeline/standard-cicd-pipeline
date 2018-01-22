//#! groovy

def GCLOUD_PROJECT = params.GCLOUD_PROJECT //"bot-operations-dev"

def dockerDaemonHost = "tcp://jenkins-docker-daemon.wizeline.com:2375"
def docker_bin="./docker -H $dockerDaemonHost"

def dockerRegistryOriginal = params.DOCKER_REGISTRY_ORIGINAL // "devops.wize.mx:5000"
def dockerImageNameOriginal = params.DOCKER_IMAGE_NAME_ORIGINAL // "bot-operations-broker"
def dockerImageTagOriginal = params.DOCKER_IMAGE_TAG_ORIGINAL // "a9585872b9465f8b1ad44e771e3dd539ae63275b"

def dockerRegistryNew = params.DOCKER_REGISTRY_NEW // "us.gcr.io/bot-operations-dev"
def dockerImageNameNew = params.DOCKER_IMAGE_NAME_NEW // "bot-operations-broker"
def dockerImageTagNew = params.DOCKER_IMAGE_TAG_NEW // "latest-test"


node {
  stage("push-to-gcloud") {

    deleteDir()

    def workspace = pwd()

    withCredentials([
        file(
          credentialsId: 'gcloud_bot_operations_dev_credentials',
          variable: 'BOT_OPERATION_DEV'),
        usernamePassword(
          credentialsId: 'wizehub',
          passwordVariable: 'DOCKER_REGISTRY_PASSWORD',
          usernameVariable: 'DOCKER_REGISTRY_USERNAME')
      ]) {

      echo "Write the script used by remote docker container"
      sh """
      cp -f $BOT_OPERATION_DEV credentials.json
      cat >init.sh <<EOF

      set -x

      curl -O https://master.dockerproject.org/linux/x86_64/docker
      chmod +x docker
      export PATH=\$PATH:/google-cloud-sdk/bin:\$(pwd)
      echo \$PATH
      cp ./docker /usr/bin/docker

      export DOCKER_TLS_VERIFY=
      $docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD $dockerRegistryOriginal
      $docker_bin pull $dockerRegistryOriginal/$dockerImageNameOriginal:$dockerImageTagOriginal
      $docker_bin tag $dockerRegistryOriginal/$dockerImageNameOriginal:$dockerImageTagOriginal $dockerRegistryNew/$dockerImageNameNew:$dockerImageTagNew

      gcloud auth activate-service-account --key-file=credentials.json
      gcloud config set project $GCLOUD_PROJECT
      gcloud docker -- version
      gcloud docker --docker-host="$dockerDaemonHost" -- push $dockerRegistryNew/$dockerImageNameNew:$dockerImageTagNew

EOF"""

      echo "Download docker client to node"
      sh """
      # Local
      curl -O https://master.dockerproject.org/linux/x86_64/docker
      chmod +x docker
      """

      echo "Test local docker client and login to registry"
      sh "export DOCKER_TLS_VERIFY= && $docker_bin version"
      sh "export DOCKER_TLS_VERIFY= && $docker_bin login -u $DOCKER_REGISTRY_USERNAME -p $DOCKER_REGISTRY_PASSWORD $dockerRegistryOriginal"


      echo "Call the buidler container"
      exit_code = sh script: """
      export DOCKER_TLS_VERIFY=
      docker_id=\$($docker_bin create -w /project --rm google/cloud-sdk:alpine ash init.sh)
      $docker_bin cp $workspace/. \$docker_id:/project
      $docker_bin start -ai \$docker_id || EXIT_CODE=\$? && true

      [ ! -z "\$EXIT_CODE" ] && exit \$EXIT_CODE;
      exit 0
      """, returnStatus: true

      echo "Ensure every exited container has been removed"
      sh script: """
      export DOCKER_TLS_VERIFY=
      containers=\$($docker_bin ps -a | grep Exited | awk '{print \$1}')
      [ -n "\$containers" ] && $docker_bin rm -f \$containers || exit 0
      """, returnStatus: true

      if (exit_code != 0){
        echo "FAILURE"
        currentBuild.result = 'FAILURE'
        error("FAILURE - Build container returned non 0 exit code")
        return 1
      }

    }
  }
}
