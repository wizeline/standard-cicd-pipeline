#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from jenkinsctl.flows import AbstractFlow
from jenkinsctl.utils import JobTemplate
from jenkinsctl.definitions import ROOT_DIR, TEMPLATES_FOLDERS_DIR


# This class is used to create:
# - One project folder
# - a terraform-control job
class TerraformFlow(AbstractFlow):
    generic_dispatcher = None
    generic_dispatcher_trigger = None
    terraform_control = None
    project_folder = None
    prefix = ""
    default_params = {
        "git_repo_url": "",
        "git_sha": "",
        "tf_source_relative_path": "",
        "tf_aws_access_credentials_id": "",
        "tf_aws_region": "",
        "tf_aws_backend_bucket_name": "",
        "tf_aws_backend_bucket_region": "",
        "tf_aws_backend_bucket_key_path": "",
        "slack_channel_name": "jenkins",
        "git_credentials_id": "b9abf261-0552-45f2-972d-08f3800d3d4f",
        "docker_registry_credentials_id":
            "d656f8b1-dcf6-4737-83c1-c9199fb02463",
    }

    def __init__(self, prefix="", name="", folder=None):
        self.prefix = prefix
        self.name = name
        self.folder = folder
        if not self.prefix:
            raise Exception("prefix arg is required")
        if not self.name:
            raise Exception("name arg is required")

    def _load_fields(self):
        self.load_j_server()
        self._load_terraform_control()

    def _load_terraform_control(self):
        self.terraform_control_name = f"{self.prefix}-{self.name}-terraform-control"
        self.terraform_control = JobTemplate(
          jenkins_object=self.j_server,
          name=self.terraform_control_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/terraform-control.xml.j2',
          parameters=self.parameters)

    def create(self):
        self.validate_params()
        self._load_fields()
        self.terraform_control.create(folder=self.folder)
