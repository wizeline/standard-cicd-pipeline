#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import re
import requests

from jenkinsctl.main import logger


class JenkinsCustom:

    def __init__(self, jenkins_url=None, username=None, password=None):
        self.jenkins_url = jenkins_url
        self.username = username
        self.password = password
        if not self.jenkins_url or not self.username or not self.password:
            raise Exception("Jenkins url, username and password are required")
        self.crumb = self._get_crumb()
        self.session = requests.session()

    def create_job(self, job_name, data, folder=None):
        if self.has_job(job_name, folder=folder):
            return True
        if folder:
            create_job_url = f"{self.jenkins_url}/job/{folder}/createItem"
        else:
            create_job_url = f"{self.jenkins_url}/createItem"

        headers = self._get_headers()
        params = {"name": job_name}
        req = self.session.post(
          create_job_url,
          headers=headers,
          params=params,
          data=str(data),
          auth=(self.username, self.password))
        return req.status_code == 200

    def has_job(self, job_name, folder=None):
        if folder:
            check_job_exsist_url = \
              f"{self.jenkins_url}/job/{folder}/checkJobName"
        else:
            check_job_exsist_url = f"{self.jenkins_url}/checkJobName"

        headers = self._get_headers()
        params = {"value": job_name}
        req = self.session.get(
          check_job_exsist_url,
          headers=headers,
          params=params,
          auth=(self.username, self.password))

        result = re.search(
          "A job already exists with the name",
          req.text,
          flags=re.IGNORECASE)
        return (req.status_code == 200 and (result is not None))

    def _get_crumb(self):
        logger.info("JenkinsCustom: Testing jenkins connections and auth.")
        r = requests.get(
          self.jenkins_url +
          '/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)',
          auth=(self.username, self.password),
        )
        crumb = r.text
        return crumb.split(":")[1]

    def _get_headers(self):
        return {"content-type": "text/xml", "Jenkins-Crumb": self.crumb}
