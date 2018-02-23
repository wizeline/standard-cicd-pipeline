#!/bin/ash

echo "local install"
python3 setup.py install --force

echo "build wheel"
python3 setup.py bdist_wheel -d dist
