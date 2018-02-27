package org.wizeline

public class DefaultValues implements Serializable {
  // Terraform
  public static defaultTerraformDockerRegistry              = "devops.wize.mx:5000"
  public static defaultTerraformDockerImageName             = "wize-terraform"
  public static defaultTerraformDockerImageTag              = "0.1.0"
  public static defaultTerraformDockerRegistryCredentialsId = "d656f8b1-dcf6-4737-83c1-c9199fb02463"

  public static defaultDockerRegistryCredentialsId = 'd656f8b1-dcf6-4737-83c1-c9199fb02463'
  public static defaultDockerDaemonPort = '4243'
  public static defaultDockerDaemonUrl  = 'internal-docker-daemon-elb.wize.mx'
  public static defaultDockerRegistry = "devops.wize.mx:5000"

  public static defaultSlackChannelName = 'jenkins'

  public static defaultDockerDockerfile = 'Dockerfile'
  public static defaultDockerNoTagCheck = 'false'
  public static defaultDockerDockerfileAbsolutePath = '/source'
  public static defaultDockerSourceRelativePath = '.'
  public static defaultDockerEnvTag = 'latest'

  public static defaultGitCredentialsId = ""
  public static defaultGitSha = "development"

  public static defaultMuteSlack = 'false'

  public static defaultDisableSubmodules = "true"
  public static defaultDockerDockerfile = "Dockerfile"
}
