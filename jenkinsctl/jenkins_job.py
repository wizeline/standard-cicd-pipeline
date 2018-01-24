import os
import logging
import re
import requests
from jinja2 import Template


logging.basicConfig(
  level=logging.INFO,
  format='%(asctime)s - %(levelname)s: %(message)s')
logger = logging.getLogger(__name__)


class JenkinsCustom:

    def __init__(self, jenkins_url=None, username=None, password=None):
        self.jenkins_url = jenkins_url
        self.username = username
        self.password = password
        if not self.jenkins_url or not self.username or not self.password:
            raise Exception("Jenkins url, username and password are required")
        self.crumb = self.get_crumb()
        self.session = requests.session()

    def create_job(self, job_name, data, folder=None):
        if self.has_job(job_name, folder=folder):
            return True
        if folder:
            create_job_url = f"{self.jenkins_url}/job/{folder}/createItem"
        else:
            create_job_url = f"{self.jenkins_url}/createItem"

        headers = self.get_headers()
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

        headers = self.get_headers()
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

    def get_crumb(self):
        logger.info("JenkinsCustom: Testing jenkins connections and auth.")
        r = requests.get(
          self.jenkins_url +
          '/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)',
          auth=(self.username, self.password),
        )
        crumb = r.text
        return crumb.split(":")[1]

    def get_headers(self):
        return {"content-type": "text/xml", "Jenkins-Crumb": self.crumb}


class JobTemplate:
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
                raise "Unhandled error"
                return False
        else:
            logger.warning(f"Job {self.name} alrady exist!")
        return True


# This class is used to create:
# - One project folder
# - a generic-dispatcher job
# - a git trigger for the generic dispatcher
# - A secrets scan job
class GenericAppFlow:
    j_server = None
    generic_dispatcher = None
    generic_dispatcher_trigger = None
    secrets_scan = None
    project_folder = None
    parameters = None
    prefix = ""
    default_params = {
        "github_project_url": "",
        "git_repo_url": "",
        "docker_image_name": "",
        "docker_source_rel_path": ".",
        "slack_channel_name": "jenkins",
    }

    def __init__(self, prefix=""):
        self.prefix = prefix
        if not self.prefix:
            raise Exception("prefix arg is required")

    def load_fields(self):
        self.load_j_server()
        self.load_project_folder()
        self.load_secrets_scan()
        self.load_generic_dispatcher()
        self.load_generic_dispatcher_trigger()

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

    def create(self):
        self.validate_params()
        self.load_fields()
        self.project_folder.create()
        self.generic_dispatcher.create(folder=self.project_folder_name)
        self.generic_dispatcher_trigger.create(folder=self.project_folder_name)
        self.secrets_scan.create(folder=self.project_folder_name)

    def load_j_server(self):
        jenkins_url = os.environ['JENKINS_URL']

        self.j_server = JenkinsCustom(
          jenkins_url=jenkins_url,
          username=os.environ['JENKINS_USER'],
          password=os.environ['JENKINS_TOKEN']
        )

    def load_project_folder(self):
        self.project_folder_name = f"{self.prefix}-folder"
        self.project_folder = JobTemplate(
          jenkins_object=self.j_server,
          name=self.project_folder_name,
          template_file='templates/jenkins-folder.xml.j2',
          parameters=self.parameters)

    def load_secrets_scan(self):
        self.secrets_scan_name = f"{self.prefix}-security-scan"
        self.secrets_scan = JobTemplate(
          jenkins_object=self.j_server,
          name=self.secrets_scan_name,
          template_file='templates/secrets-scan.xml.j2',
          parameters=self.parameters)

    def load_generic_dispatcher(self):
        self.generic_dispatcher_name = f"{self.prefix}-dispatcher"
        self.generic_dispatcher = JobTemplate(
          jenkins_object=self.j_server,
          name=self.generic_dispatcher_name,
          template_file='templates/generic-app-flow.xml.j2',
          parameters=self.parameters)

    def load_generic_dispatcher_trigger(self):
        self.generic_dispatcher_trigger_name = \
            f"{self.prefix}-dispatcher-trigger"
        self.generic_dispatcher_trigger = JobTemplate(
          jenkins_object=self.j_server,
          name=self.generic_dispatcher_trigger_name,
          template_file='templates/generic-app-flow-trigger.xml.j2',
          parameters=self.parameters)


def main():
    gaf = GenericAppFlow(prefix="saul-tests-delete")
    gaf.set_parameters({
        "github_project_url":
        "https://github.com/wizeline/standard-cicd-pipeline/",
        "git_repo_url": "git@github.com:wizeline/wz-statuspage.git",
        "docker_image_name": "cachet-backend",
        "docker_source_rel_path": "backend",
        "slack_channel_name": "jenkins",
    })
    gaf.create()


if __name__ == "__main__":
    main()
