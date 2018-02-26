"""Installation module
"""

import os
import sys

from pip.req import parse_requirements
from setuptools import setup

PYTHON_VERSION = '3.6'


class UnsupportedPythonVersion(Exception):
    def __init__(self):
        super().__init__('Your current version "{0.major}.{0.minor}" is less than the minimum'
                         ' "{1}"'.format(sys.version_info, PYTHON_VERSION))


cmp = tuple([int(digit) for digit in PYTHON_VERSION.split('.')])
if (sys.version_info.major, sys.version_info.minor) < cmp:
    raise UnsupportedPythonVersion

install_reqs = parse_requirements(
    f'{os.path.dirname(os.path.abspath(__file__))}/requirements-common.txt',
    session='hack'
)


def read(fname):
    return open(os.path.join(os.path.dirname(__file__), fname)).read()


setup(
    name="jenkinsctl",
    version="0.1.0",
    author="Wizeline",
    author_email="devops@wizeline.com",
    description=("A commandline tool to create jenkins jobs"),
    license="MIT",
    keywords="jenkinsctl",
    packages=[
        'jenkinsctl',
        'jenkinsctl.flows',
        'jenkinsctl.interfaces',
        'jenkinsctl.utils'
    ],
    install_requires=[str(i_req.req) for i_req in install_reqs],
    long_description=read('README.md'),
    include_package_data=True,
    entry_points={
        "console_scripts": [
            'jenkinsctl = jenkinsctl.main:main'
        ]
    }
)
