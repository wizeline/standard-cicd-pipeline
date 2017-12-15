#!/bin/bash
kubectl -n jenkins delete secret jenkins 
kubectl -n jenkins create secret generic jenkins --from-file=./options
