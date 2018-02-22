variable "name" {}

# VPC Info
variable "vpc_id" {}
variable "vpc_cidr" {}
variable "region" {}
variable "vpc_subnets_ids" {
  type    = "list"
  #default = ["subnet-e8c83080", "subnet-389ca043", "subnet-389ca043"]
}

# Tags Info
variable "project" {}
variable "environment" {}
variable "service" {}
variable "customer" {}

# Instances Info
variable "instance_type" {
  default = "t2.micro"
}
variable "key_name" {}

# Consul
variable "acl_master_token" {}
variable "consul_encrypt" {}


# Data
data "aws_ami" "coreos" {
  most_recent = true

  filter {
    name   = "name"
    values = ["CoreOS-stable-*-hvm"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  owners = ["595879546273"]
}

data "template_file" "user_data" {
  template = "${file("${path.module}/user_data.sh")}"

  vars {
    acl_master_token = "${var.acl_master_token}"
    consul_encrypt = "${var.consul_encrypt}"
  }
}
