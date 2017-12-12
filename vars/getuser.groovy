def call(){
  
  def build = currentBuild.rawBuild
  def cause = build.getCause(hudson.model.Cause.UserIdCause.class)
  def name = cause.getUserName()
  return name

    //wrap([$class: 'BuildUser']) {
    //  return BUILD_USER
    //}
}
