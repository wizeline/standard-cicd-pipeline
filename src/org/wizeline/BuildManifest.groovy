package org.wizeline

public class BuildManifest implements Serializable {
  // Other
  private steps
  private params
  private env
  private config
  private build_user
  private git_info

  BuildManifest(steps, params, env, config, build_user, git_info, job_type) {
    this.steps = steps
    this.params = params
    this.env = env
    this.config = config
    this.build_user = build_user
    this.git_info = git_info
    this.job_type = job_type
  }

  // @NonCPS
  private def build_manifest(){
    // def curr_date = new Date()

    return {
      "jenkins": {
        // "build_datetime": curr_date.format("yyMMdd.HHmm", TimeZone.getTimeZone('UTC'))
        // "build_timestamp": this.steps.currentBuild.startTimeInMillis,
        "build_number": this.env.BUILD_NUMBER,
        "build_url": this.env.BUILD_URL,
        "job_name": this.env.JOB_NAME,
        "build_tag": this.env.BUILD_TAG,
        "build_user": this.build_user,
        "job_type": this.job_type,
      },
      "git": {
        "git_branch": this.git_info["git-branch"],
        "git_sha": this.git_info["git-commit-sha"],
        "git_author": this.git_info["git-author"],
        "git_sha_arg": this.git_info["git-sha-arg"],
        "git_repo_url": this.git_info["git-repo-url"],
      }
    }
  }

  // @NonCPS
  public def generate(){
    def manifest = this.build_manifest()
    // println()
  }

}
