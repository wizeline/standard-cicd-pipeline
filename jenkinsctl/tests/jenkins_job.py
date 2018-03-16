#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from jenkinsctl.flows import *


def main():
    gaf = GenericAppFlow(prefix="saul-tests-delete")
    gaf.set_parameters({
        "github_project_url":
        "https://github.com/wizeline/wz-statuspage/",
        "git_repo_url": "git@github.com:wizeline/wz-statuspage.git",
        "docker_image_name": "cachet-backend",
        "docker_source_rel_path": "backend",
        "slack_channel_name": "jenkins",
    })
    gaf.create()

    kdf = KubernetesDeployerFlow(prefix="saul-tests-delete")
    kdf.set_parameters({
        "k8s_credentials_id": "k8s.config.devops-clusters",
        "k8s_context": "devops-kops-cluster.wize.mx",
        "k8s_namespace": "status-page",
        "k8s_deployment_name": "cachet-backend-develop",
        "k8s_env_tag": "develop",
        "slack_channel_name": "jenkins",
        "docker_image_name": "cachet-backend",
    })
    kdf.create()

    kdf = KubernetesDeployerFlow(prefix="saul-tests-delete")
    kdf.set_parameters({
        "k8s_credentials_id": "k8s.config.devops-clusters",
        "k8s_context": "devops-kops-cluster.wize.mx",
        "k8s_namespace": "status-page",
        "k8s_deployment_name": "cachet-backend-production",
        "k8s_env_tag": "production",
        "slack_channel_name": "jenkins",
        "docker_image_name": "cachet-backend",
    })
    kdf.create()

    tff = TerraformFlow(prefix="saul-tests-delete")
    tff.set_parameters({
        "git_repo_url": "http://phabricator.wizeline.com/diffusion/29/terraform-jmeter.git",
        "git_credentials_id": "sortigoza - phabricator",
        "git_sha": "master",
        "tf_source_relative_path": "cluster",
        "tf_aws_access_credentials_id": "jenkins-terraform",
        "tf_aws_region": "us-west-2",
        "tf_aws_backend_bucket_name": "wizeline-devops-terraform",
        "tf_aws_backend_bucket_region": "us-east-1",
        "tf_aws_backend_bucket_key_path": "jmeter-cluster/production",
        "slack_channel_name": "jenkins",
    })
    tff.create()


if __name__ == "__main__":
    main()
