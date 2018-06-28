package org.wizeline
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLConnection

// (measurement) jenkins_build
// Event_timestamp: timestamp
// (tag) Job_id: string
// (tag) Job_type: enum (build, unit-test, deploy, test)
// (tag) Job_initiator: string
// (field) Job_number: int
// (field) Job_state: enum (started, fail, pass, unstable)


public class InfluxMetrics implements Serializable {
  public static STARTED = 0
  public static PASSED = 1
  public static UNSTABLE = -1
  public static FAILED = 2

  public String measurement_name = 'jenkins_build'
  // tags
  public String job_id
  public String job_type
  public String job_initiator
  // fields
  public String job_number
  public String jenkins_url
  // influx
  private String influxAPIAuth
  private String influxURL
  private String influxDb = "cicd"
  // Other
  private steps
  private params
  private env
  private config
  private build_user

  InfluxMetrics(steps, params, env, config, build_user, job_type, influxURL, influxAPIAuth) {
    this.steps = steps
    this.params = params
    this.env = env
    this.config = config
    this.build_user = build_user

    this.job_type = this.formatField(job_type)
    this.job_number = this.formatField(env.BUILD_NUMBER)
    this.job_id = this.formatField(env.JOB_NAME)
    this.jenkins_url = this.formatField(env.JENKINS_URL)
    this.job_initiator = this.build_user
    // env.GIT_URL
    // env.GIT_BRANCH
    // env.GIT_COMMIT
  }

  @NonCPS
  private String formatField(field){
    return field.replace(" ", "_")
  }

  // @NonCPS
  private def sendPostRequest(urlString, requestBody) {
      def response = this.steps.httpRequest
        httpMode: "POST",
        contentType: "TEXT_PLAIN",
        customHeaders: [[name: 'Authorization', value: this.influxAPIAuth]],
        requestBody: requestBody,
        url: urlString
      return response
  }

  // @NonCPS
  public def sendInfluxPoint(a_job_state) {
    job_state = a_job_state
    measurement_tags = "job_id=${this.job_id},job_type=${this.job_type},job_initiator=${this.job_initiator}"
    values = "value=${job_state},job_number=${this.job_number},jenkins_url=${this.jenkins_url}"
    data = "${this.measurement_name},${measurement_tags} ${values}"
    post_url = "${this.influxURL}/write?db=${this.influxDb}"

    this.sendPostRequest(post_url, data)
  }

  public def processBuildResult(currentBuild) {
    if (currentBuild.result == 'SUCCESS') {
      this.sendInfluxPoint(influxdb.PASSED)
    } else if (currentBuild.result == 'UNSTABLE') {
      this.sendInfluxPoint(influxdb.UNSTABLE)
    } else {
      this.sendInfluxPoint(influxdb.FAILED)
    }
  }

}
