provider "aws" {
  region = "${var.region}"
  profile = "devops"
}

resource "aws_security_group" "dockerd" {
  name        = "${var.name}"
  vpc_id      = "${var.vpc_id}"
  description = "Docker daemon server security group"

  tags {
    Name        = "${var.name}"
    project     = "${var.project}"
    environment = "${var.environment}"
    service     = "${var.service}"
    customer    = "${var.customer}"
  }

  ingress {
    protocol    = "tcp"
    from_port   = 22
    to_port     = 22
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    protocol    = "tcp"
    from_port   = 4567
    to_port     = 4567
    cird_blocks = ["0.0.0.0/0"]
  }

  ingress {
    protocol    = "tcp"
    from_port   = 4243
    to_port     = 4243
    cidr_blocks = ["${var.vpc_cidr}"]
  }

  egress {
    protocol    = -1
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_launch_configuration" "dockerds" {
  name_prefix   = "${var.name}-lc-"
  image_id      = "${data.aws_ami.coreos.id}"
  instance_type = "${var.instance_type}"
  security_groups = ["${aws_security_group.dockerd.id}"]

  key_name = "${var.key_name}"

  user_data = "${data.template_file.user_data.rendered}"

  lifecycle {
    create_before_destroy = true
  }
}


resource "aws_elb" "elb1" {
  name = "${var.name}-elb"
  subnets = ["${var.vpc_subnets_ids}"]
  internal = true
  security_groups = ["${aws_security_group.dockerd.id}"]

  listener {
    instance_port = 22
    instance_protocol = "TCP"
    lb_port = 22
    lb_protocol = "TCP"
  }

  listener {
    instance_port = 4243
    instance_protocol = "TCP"
    lb_port = 4243
    lb_protocol = "TCP"
  }

  health_check {
    healthy_threshold = 2
    unhealthy_threshold = 2
    timeout = 3
    target = "TCP:22"
    interval = 30
  }

  tags {
    Name        = "${var.name}"
    project     = "${var.project}"
    environment = "${var.environment}"
    service     = "${var.service}"
    customer    = "${var.customer}"
  }
}

resource "aws_autoscaling_group" "dockerds" {
  name                 = "${var.name}-asg"
  launch_configuration = "${aws_launch_configuration.dockerds.name}"
  vpc_zone_identifier  =  ["${var.vpc_subnets_ids}"]
  min_size             = 2
  max_size             = 2

  load_balancers= ["${aws_elb.elb1.id}"]

  lifecycle {
    create_before_destroy = true
  }

  tags = [
    {
      key                 = "Name"
      value               = "${var.name}-asg"
      propagate_at_launch = true
    },
    {
      key                 = "project"
      value               = "${var.project}"
      propagate_at_launch = true
    },
    {
      key                 = "environment"
      value               = "${var.environment}"
      propagate_at_launch = true
    },
    {
      key                 = "service"
      value               = "${var.service}"
      propagate_at_launch = true
    },
    {
      key                 = "customer"
      value               = "${var.customer}"
      propagate_at_launch = true
    },
  ]

}
