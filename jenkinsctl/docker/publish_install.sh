#!/bin/ash

set -e

echo "publish to s3 bucket"
s3pypi --bucket python-s3pypi-repository --force

echo "install from s3 bucket"
PIP3_URL=http://python-s3pypi-repository.s3-website-us-west-2.amazonaws.com/
TRUSTED_HOST=python-s3pypi-repository.s3-website-us-west-2.amazonaws.com

pip3 install jenkinsctl -v \
  --extra-index-url $PIP3_URL \
  --trusted-host $TRUSTED_HOST
