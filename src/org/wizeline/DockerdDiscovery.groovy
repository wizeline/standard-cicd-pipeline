package org.wizeline

public class DockerdDiscovery implements Serializable {
  // @NonCPS
  public static def getDockerDaemon(steps, dockerDaemonHost, dockerDaemonPort, dockerDaemonDnsDiscovery) {
      if (!dockerDaemonHost){
        dockerDaemonHost = steps.sh(script: "dig +short ${dockerDaemonDnsDiscovery} | head -n 1", returnStdout: true).trim()
      }
      def dockerDaemon = "tcp://${dockerDaemonHost}:${dockerDaemonPort}"
      return dockerDaemon
   }
}
