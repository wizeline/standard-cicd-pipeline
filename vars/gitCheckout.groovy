//#!Groovy
def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  checkout([$class: 'GitSCM',
            branches: [[name: config.branch]],
            doGenerateSubmoduleConfigurations: true,
            extensions: [[$class: 'SubmoduleOption',
                          disableSubmodules: true,
                          parentCredentials: true,
                          recursiveSubmodules: true,
                          reference: '',
                          trackingSubmodules: false]],
            submoduleCfg: [],
            userRemoteConfigs: [
              [
                refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull/*:refs/remotes/origin/pr/*',
                credentialsId: config.credentialsId,
                url: config.repoUrl]
            ]
        ])

  return_hash = [:]
  return_hash["git-branch"] = sh(returnStdout:true, script:'git rev-parse --abbrev-ref HEAD').trim()
  return_hash["git-commit-sha"] = sh(returnStdout:true, script:'git rev-parse HEAD').trim()

  return return_hash
}