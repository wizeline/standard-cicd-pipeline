//#!Groovy
def call(body) {

  def config = [:]

  if (body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
  }

  echo "gitCheckout.groovy"
  print config

  def disableSubmodules = (config.disableSubmodules == "true") ? true : false
  println "disableSubmodules: ${disableSubmodules}"

  checkout([$class: 'GitSCM',
            branches: [[name: config.branch]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'SubmoduleOption',
                          disableSubmodules: disableSubmodules,
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

  sh "ls -la"
  sh "git ls-tree -r HEAD | head -n 50"

  return_hash = [:]
  return_hash["git-branch"] = sh(returnStdout:true, script:'git rev-parse --abbrev-ref HEAD').trim()
  return_hash["git-commit-sha"] = sh(returnStdout:true, script:'git rev-parse HEAD').trim()
  return_hash["git-author"] = sh(returnStdout:true, script:"git log -1 --pretty=format:'%aN <%aE>'").trim()
  return_hash["git-sha-arg"] = config.branch
  return_hash["git-repo-url"] = config.repoUrl

  return return_hash
}
