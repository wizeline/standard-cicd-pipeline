#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from jenkinsctl.logger import logger
from jinja2 import Template


class JobTemplate(object):
    parameters = {}
    _jenkins_object = None
    _template_file = ""
    _template_object = None

    def __init__(self,
                 jenkins_object=None,
                 name=None,
                 template_file=None,
                 parameters={}):
        self.parameters = parameters
        self.name = name
        self._jenkins_object = jenkins_object
        self._template_file = template_file

    def render_config(self):
        with open(self._template_file) as f:
            self._template_object = Template(f.read().strip())
        return self._template_object.render(self.parameters)

    def create(self, folder=None):
        if not self._jenkins_object.has_job(self.name, folder=folder):
            res = self._jenkins_object.create_job(
              self.name,
              self.render_config(),
              folder=folder
            )
            if res:
                logger.info(f"Job {self.name} created!")
                return True
            else:
                raise Exception("Unhandled error")
                return False
        else:
            logger.warning(f"Job {self.name} alrady exist!")
        return True
