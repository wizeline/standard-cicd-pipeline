# standard-cicd-pipeline
Standard Pipeline


# Docker Images

### Jenkins Slave

Docker jenkins slave image customized to have binaries used for building and deploying.

# Kubernetes (k8s)

### Jenkins

These manifests deploy a jenkins master and two slaves.

Some manual configuration has to be made to connect the slave nodes.

# Terraform

In this dir you will find script to create a cluster of docker daemons.
This daemons use the Container Linux AMI and is configured to expose dockerd in tcp.
Instances are managed by an ASG.

Manual steps:
-  Configure an A record to point to the instances. Jenkins jobs will get the
IPs from here to connect to a cluster instance.


# Vars

Shared Library for jenkins standar pipelines.
