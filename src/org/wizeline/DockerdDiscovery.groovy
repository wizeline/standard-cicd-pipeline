package org.wizeline

public class DockerdDiscovery implements Serializable {
  static def getDockerDaemon(dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery) {
      if (!dockerDaemonHost){
        dockerDaemonHost = sh(script: "dig +short ${dockerDaemonDnsDiscovery} | head -n 1", returnStdout: true).trim()
      }
      dockerDaemon = "tcp://${dockerDaemonHost}:${dockerDaemonPort}"
      return dockerDaemon
   }
}
