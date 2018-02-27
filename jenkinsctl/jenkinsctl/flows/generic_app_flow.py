#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from jenkinsctl.flows import AbstractFlow
from jenkinsctl.utils import JobTemplate
from jenkinsctl.definitions import ROOT_DIR, TEMPLATES_FOLDERS_DIR


# This class is used to create:
# - One project folder
# - a generic-dispatcher job
# - a git trigger for the generic dispatcher
# - A secrets scan job
class GenericAppFlow(AbstractFlow):
    generic_dispatcher = None
    generic_dispatcher_trigger = None
    secrets_scan = None
    project_folder = None
    prefix = ""
    default_params = {
        "github_project_url": "",
        "git_repo_url": "",
        "docker_image_name": "",
        "docker_source_rel_path": ".",
        "slack_channel_name": "jenkins",
        "docker_dockerfile": "Dockerfile",
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
        self._load_secrets_scan()
        self._load_generic_dispatcher()
        self._load_generic_dispatcher_trigger()

    def _load_project_folder(self):
        self.project_folder_name = f"{self.prefix}-folder"
        self.project_folder = JobTemplate(
          jenkins_object=self.j_server,
          name=self.project_folder_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/jenkins-folder.xml.j2',
          parameters=self.parameters)

    def _load_secrets_scan(self):
        self.secrets_scan_name = f"{self.prefix}-security-scan"
        self.secrets_scan = JobTemplate(
          jenkins_object=self.j_server,
          name=self.secrets_scan_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/secrets-scan.xml.j2',
          parameters=self.parameters)

    def _load_generic_dispatcher(self):
        self.generic_dispatcher_name = f"{self.prefix}-dispatcher"
        self.parameters["secrets_scan_job"] = self.secrets_scan_name
        self.generic_dispatcher = JobTemplate(
          jenkins_object=self.j_server,
          name=self.generic_dispatcher_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/generic-app-flow.xml.j2',
          parameters=self.parameters)

    def _load_generic_dispatcher_trigger(self):
        self.parameters["dispatcher_job"] = self.generic_dispatcher_name
        self.generic_dispatcher_trigger_name = \
            f"{self.prefix}-dispatcher-trigger"
        self.generic_dispatcher_trigger = JobTemplate(
          jenkins_object=self.j_server,
          name=self.generic_dispatcher_trigger_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/generic-app-flow-trigger.xml.j2',
          parameters=self.parameters)

    def create(self):
        self.validate_params()
        self._load_fields()
        self.project_folder.create()
        self.secrets_scan.create(folder=self.project_folder_name)
        self.generic_dispatcher.create(folder=self.project_folder_name)
        self.generic_dispatcher_trigger.create(folder=self.project_folder_name)
