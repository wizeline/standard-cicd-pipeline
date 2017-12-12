def call(){
  // 
  // try {
  //   def build = currentBuild.rawBuild
  //   def cause = build.getCause(hudson.model.Cause.UserIdCause.class)
  //   def name = cause.getUserName()
  //   return name
  // }
  // catch (Exception e) {
  //   println "Exception ${e.message}. This errors occur when script runs in the sandbox"
  //   return "Unknown"
  // }

  return "Unknown"
    //wrap([$class: 'BuildUser']) {
    //  return BUILD_USER
    //}
}
