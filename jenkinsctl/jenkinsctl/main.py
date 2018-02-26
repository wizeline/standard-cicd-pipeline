#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import logging

logging.basicConfig(
  level=logging.INFO,
  format='%(asctime)s - %(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

from jenkinsctl.interfaces.jenkinscmd import cli


def main():
    cli()


if __name__ == "__main__":
    main()
