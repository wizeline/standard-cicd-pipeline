#!/bin/bash
kubectl -n jenkins delete secret jenkins
kubectl -n jenkins create secret generic jenkins --from-file=./options --from-file=secret.slave-1 --from-file=secret.slave-2
