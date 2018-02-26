#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import time

from jenkinsctl.logger import logger
from jenkinsctl.utils import JobTemplate, JenkinsCustom
from jenkinsctl.definitions import ROOT_DIR, TEMPLATES_FOLDERS_DIR


class JenkinsPlugins(object):
    PLUGINS_FILENAME = 'jenkins_plugins.txt'
    PLUGINS_CONFIG_TEMPLATE = 'plugin-payload.xml'

    def _load_j_server(self):
        self.j_server = JenkinsCustom.create_j_server()

    def _load_plugins_list(self):
        self.plugins = []

        with open(f"{TEMPLATES_FOLDERS_DIR}/{self.PLUGINS_FILENAME}") as fdesc:
            # strip ?? replace('\n', '')
            self.plugins = [plugin.strip() for plugin in fdesc.readlines()]

    def _render_plugins_xml(self):
        tf = f'{TEMPLATES_FOLDERS_DIR}/{self.PLUGINS_CONFIG_TEMPLATE}'
        self.jt_data = JobTemplate(
          jenkins_object=None,
          name=None,
          template_file=tf,
          parameters={"plugins": self.plugins}).render_config()

    def _install_jenkins_plugins(self):
        self.j_server.install_plugins(self.jt_data)

    def _wait_for_plugins(self):
        PLUGIN_HEALTHCHECK = 2  # 2 Seconds
        TIMEOUT = 60*5   # 5 minutes from now
        total_plugins = len(self.plugins)

        timeout = time.time() + TIMEOUT
        while True:
            data = self.j_server.get_plugins_list()
            current_amount = len(data['plugins'])

            logger.info(f'{current_amount}/{total_plugins} plugins installed')

            if current_amount >= total_plugins or time.time() > timeout:
                break

            time.sleep(PLUGIN_HEALTHCHECK)

    def run(self):
        self._load_j_server()
        self._load_plugins_list()
        self._render_plugins_xml()
        self._install_jenkins_plugins()
        self._wait_for_plugins()
