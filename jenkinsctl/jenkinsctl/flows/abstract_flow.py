#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

from jenkinsctl.main import logger
from jenkinsctl.utils import JenkinsCustom


class AbstractFlow:
    j_server = None
    parameters = None
    default_params = {}

    def set_parameters(self, parameters):
        self.parameters = {**self.default_params, **parameters}

    def validate_params(self):
        if not self.parameters:
            err = "You have to set parameters first."
            logger.critical(err)
            raise Exception("Parameter error")
        for k, v in self.default_params.items():
            if not self.parameters[k]:
                err = f"Parameter {k} is required."
                logger.critical(err)
                raise Exception("Parameter error")

    def load_j_server(self):
        jenkins_url = os.environ['JENKINS_URL']

        self.j_server = JenkinsCustom(
          jenkins_url=jenkins_url,
          username=os.environ['JENKINS_USER'],
          password=os.environ['JENKINS_TOKEN']
        )
