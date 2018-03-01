package org.wizeline

public class DockerdDiscovery implements Serializable {
  // @NonCPS
  static def getDockerDaemon(steps, dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery) {
      if (!dockerDaemonHost){
        dockerDaemonHost = steps.sh(script: "dig +short ${dockerDaemonDnsDiscovery} | head -n 1", returnStdout: true).trim()
      }
      dockerDaemon = "tcp://${dockerDaemonHost}:${dockerDaemonPort}"
      return dockerDaemon
   }
}
