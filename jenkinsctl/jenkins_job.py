from jinja2 import Template
import os
import jenkinsapi
from jenkinsapi.jenkins import Jenkins
from jenkinsapi.utils.crumb_requester import CrumbRequester


class JobTemplate:
    parameters = {}
    _jenkins_object = None
    _template_file = ""
    _template_object = None

    def __init__(self,
                 jenkins_object=None,
                 name=None,
                 template_file=None,
                 parameters=None):
        self.parameters = parameters
        self.name = name
        self._jenkins_object = jenkins_object
        self._template_file = template_file

    def render_config(self):
        with open(self._template_file) as f:
            self._template_object = Template(f.read())
        return self._template_object.render(self.parameters)

    def create(self):
        self._jenkins_object.create_job(
          self.name,
          self.render_config()
        )


jenkins_url = "https://jenkins.wize.mx/"

j_server = Jenkins(
  jenkins_url,
  username=os.environ['JENKINS_USER'],
  password=os.environ['JENKINS_TOKEN'],
  requester=CrumbRequester(
   baseurl=jenkins_url,
   username=os.environ['JENKINS_USER'],
   password=os.environ['JENKINS_TOKEN']
  )
)

print(j_server.version)

params = {
    "github_project_url": "https://github.com/wizeline/standard-cicd-pipeline/",
    "git_repo_url": "git@github.com:wizeline/wz-statuspage.git",
    "docker_image_name": "cachet-backend",
    "slack_channel_name": "jenkins",
    "docker_source_rel_path": "backend",
}
jt = JobTemplate(
  jenkins_object=j_server,
  name="test-delete-saul",
  template_file='templates/generic-app-flow.xml.j2',
  parameters=params)

print(jt.render_config())
jt.create()
