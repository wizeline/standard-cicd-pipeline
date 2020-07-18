package org.wizeline

public class SlackI implements Serializable {
    public String slackChannelName
    public String slackToken
    public boolean muteSlack
    public String git_sha
    public String job_name
    public String build_number
    public String build_url
    public String build_user
    public String sufix
    private steps
    private params
    private env
    private config

    // steps, params, env, config, build_user
    SlackI(steps, params, env, config, build_user) {
      this.steps = steps
      this.params = params
      this.env = env
      this.config = config

      def slackChannelName = params.SLACK_CHANNEL_NAME ?: 'jenkins'
      this.slackChannelName = config.slackChannelName ?: slackChannelName
      this.slackToken = config.slackToken
      def tmpMuteSlack = config.muteSlack ?: 'false'
      this.muteSlack = (tmpMuteSlack == 'true')

      this.git_sha = "${params.GIT_SHA}"
      this.job_name = "${env.JOB_NAME}"
      this.build_number = "${env.BUILD_NUMBER}"
      this.build_url = "${env.BUILD_URL}"
      this.build_user = build_user

      loadSufix()
    }

    @NonCPS
    private void loadSufix(){
      this.sufix = "\n${this.git_sha}:${this.job_name} - ${this.build_number}\n(${this.build_url})\n*Build started by* :${this.build_user}"
    }

    @NonCPS
    private void useK8sSufix(){
      def job_name_number = "${this.job_name} - ${this.build_number}\n(${this.build_url})\n"
      def deployment_artifact = "${config.dockerImageName}:${config.dockerImageTag}"
      def deployment_env = "${config.k8sContext}:${config.k8sNamespace}:${config.k8sDeploymentName}"
      this.sufix = "\n${job_name_number}${deployment_artifact} in ${deployment_env}"
    }

    @NonCPS
    private void useTestsSufix(){
      def job_name_number = "${this.job_name} - ${this.build_number}\n(${this.build_url})\n"
      this.sufix = "\n${job_name_number}"
    }

    def send(color, message){
      if (this.slackChannelName && !this.muteSlack) {
        this.steps.slackSend channel: "#${this.slackChannelName}",
                              color: color,
                              message: "${message}${this.sufix}"
      }
    }
}
