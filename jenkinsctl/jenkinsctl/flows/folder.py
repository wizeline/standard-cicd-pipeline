#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from jenkinsctl.flows import AbstractFlow
from jenkinsctl.utils import JobTemplate
from jenkinsctl.definitions import ROOT_DIR, TEMPLATES_FOLDERS_DIR


# This class is used to create:
# - One project folder
# - a terraform-control job
class FolderCreate(AbstractFlow):
    generic_dispatcher = None
    generic_dispatcher_trigger = None
    terraform_control = None
    project_folder = None
    prefix = ""
    default_params = {
        "git_standard_cicd_credentials_id":
            "b9abf261-0552-45f2-972d-08f3800d3d4f",
        "git_standard_cicd_version": "develop",
    }

    def __init__(self, prefix="", name=""):
        self.prefix = prefix
        self.name = name
        if not self.prefix:
            raise Exception("prefix arg is required")
        if not self.name:
            raise Exception("name arg is required")

    def _load_fields(self):
        self.load_j_server()
        self._load_project_folder()

    def _load_project_folder(self):
        self.project_folder_name = f"{self.prefix}-{self.name}"
        self.project_folder = JobTemplate(
          jenkins_object=self.j_server,
          name=self.project_folder_name,
          template_file=f'{TEMPLATES_FOLDERS_DIR}/jenkins-folder.xml.j2',
          parameters=self.parameters)

    def create(self):
        self.validate_params()
        self._load_fields()
        self.project_folder.create()
