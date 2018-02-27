#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from jenkinsctl.flows import AbstractFlow
from jenkinsctl.utils import JobTemplate
from jenkinsctl.definitions import ROOT_DIR, TEMPLATES_FOLDERS_DIR


class KubernetesDeployerFlow(AbstractFlow):
    project_folder = None
    k8s_deployer = None
    k8s_trigger = None
    prefix = ""
    default_params = {
        "k8s_credentials_id": "",
        "k8s_context": "",
        "k8s_namespace": "",
        "k8s_deployment_name": "",
        "k8s_env_tag": "",
        "docker_image_name": "",
        "slack_channel_name": "jenkins",
        "docker_registry": "devops.wize.mx:5000",
        "git_standard_cicd_credentials_id":
            "b9abf261-0552-45f2-972d-08f3800d3d4f",
        "git_credentials_id": "b9abf261-0552-45f2-972d-08f3800d3d4f",
        "git_standard_cicd_version": "develop",
        "docker_registry_credentials_id":
            "d656f8b1-dcf6-4737-83c1-c9199fb02463",
    }

    def __init__(self, prefix=""):
        self.prefix = prefix
        if not self.prefix:
            raise Exception("prefix arg is required")

    def _load_fields(self):
        self.load_j_server()
        self._load_project_folder()
        self._load_k8s_deployer()
        self._load_k8s_trigger()

    def _load_project_folder(self):
        self.project_folder_name = f"{self.prefix}-folder"
        self.project_folder = JobTemplate(
          jenkins_object=self.j_server,
          name=self.project_folder_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/jenkins-folder.xml.j2',
          parameters=self.parameters)

    def _load_k8s_deployer(self):
        self.k8s_deployer_name = f"{self.prefix}-k8s-deployer"
        self.k8s_deployer = JobTemplate(
          jenkins_object=self.j_server,
          name=self.k8s_deployer_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/k8s-deployer.xml.j2',
          parameters=self.parameters)

    def _load_k8s_trigger(self):
        environment = self.parameters['k8s_env_tag']
        self.k8s_trigger_name = f"{self.prefix}-k8s-deploy-{environment}"
        self.parameters["deployer_job"] = self.k8s_deployer_name
        self.k8s_trigger = JobTemplate(
          jenkins_object=self.j_server,
          name=self.k8s_trigger_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/k8s-deployer-trigger.xml.j2',
          parameters=self.parameters)

    def create(self):
        self.validate_params()
        self._load_fields()
        self.project_folder.create()
        self.k8s_deployer.create(folder=self.project_folder_name)
        self.k8s_trigger.create(folder=self.project_folder_name)
