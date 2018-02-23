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


if __name__ == "__main__":
    main()
