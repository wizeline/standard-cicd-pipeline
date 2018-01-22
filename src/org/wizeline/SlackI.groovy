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

    // steps, config, params, env, build_user
    SlackI(params) {
      this.steps = params.steps

      this.slackChannelName = params.config.slackChannelName ?: 'jenkins'
      this.slackToken = params.config.slackToken
      def tmpMuteSlack = params.config.muteSlack ?: 'false'
      this.muteSlack = (tmpMuteSlack == 'true')

      this.git_sha = "${params.params.GIT_SHA}"
      this.job_name = "${params.env.JOB_NAME}"
      this.build_number = "${params.env.BUILD_NUMBER}"
      this.build_url = "${params.env.BUILD_URL}"
      this.build_user = params.build_user

      this.loadSufix()
    }

    void loadSufix(){
      this.sufix = "\n${this.git_sha}:${this.job_name} - ${this.build_number}\n(${this.build_url})\n*Build started by* :${this.build_user}"
    }

    def send(color, message){
      if (this.slackChannelName && !this.muteSlack) {
        this.steps.slackSend channel: "#${this.slackChannelName}",
                              color: color,
                              message: "${message}${this.sufix}"
      }
    }
}
