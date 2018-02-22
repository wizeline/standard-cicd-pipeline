#!/bin/bash

cat << EOF > /etc/systemd/system/docker-tcp.socket

[Unit]
Description=Docker Socket for the API

[Socket]
ListenStream=4243
BindIPv6Only=both
Service=docker.service

[Install]
WantedBy=sockets.target
EOF

# To upgrade to docker 17.x.x do:
mkdir -p /etc/coreos && echo no > /etc/coreos/docker-1.12

# Enable tcp socket
systemctl enable docker-tcp.socket
systemctl stop docker
systemctl start docker-tcp.socket
systemctl start docker
systemctl enable docker.service
systemctl start docker.service

# Setup docker cleanup timer service
cat <<EOF >> /etc/systemd/system/docker-cleanup.service
[Unit]
Description=Removes unused docker data

[Service]
Type=oneshot
ExecStart=/bin/docker system prune -f
EOF

cat <<EOF >> /etc/systemd/system/docker-cleanup.timer
[Unit]
Description=Run docker-cleanup.service every 10 minutes

[Timer]
OnCalendar=daily
AccuracySec=1h
EOF

systemctl start docker-cleanup.timer
systemctl list-timers
systemctl list-timers --all


# Install docker-compose
mkdir -p /opt/bin
curl -L https://github.com/docker/compose/releases/download/1.17.1/docker-compose-`uname -s`-`uname -m` > /opt/bin/docker-compose
chmod +x /opt/bin/docker-compose


# Install Portainer
mkdir -p /opt/portainer
git clone https://github.com/portainer/portainer-compose.git /opt/portainer

mkdir -p /opt/portainer/data
cat << EOF > /opt/portainer/docker-compose.yml
version: '2'

services:
  proxy:
    build: nginx/
    container_name: "portainer-proxy"
    ports:
      - "80:80"
    networks:
      - local
    restart: always

  templates:
    image: portainer/templates
    container_name: "portainer-templates"
    networks:
      - local
    restart: always

  portainer:
    image: portainer/portainer
    container_name: "portainer-app"
    # Automatically choose 'Manage the Docker instance where Portainer is running'
    # by adding <--host=unix:///var/run/docker.sock> to the command
    command: --templates http://templates/templates.json
    networks:
      - local
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - /opt/portainer/data:/data
    restart: always

  watchtower:
    image: v2tec/watchtower
    container_name: "portainer-watchtower"
    command: --cleanup portainer-app portainer-watchtower portainer/templates
    networks:
      - local
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    restart: always

networks:
  local:
    driver: bridge
EOF

cd /opt/portainer/ && /opt/bin/docker-compose up -d

# -- Consul Node --

# # Ips
# PRIVATE_IP=$(curl http://169.254.169.254/latest/meta-data/local-ipv4)
# PUBLIC_IP=$(curl http://169.254.169.254/latest/meta-data/public-ipv4)
# PRIVATE_IP_HIPHENS=$${PRIVATE_IP//./-}
#
# # Setup Consul
# mkdir -p /tmp/consul
# mkdir -p /etc/consul
# tee /etc/consul/server.json > /dev/null <<EOF
# {
#   "bind_addr": "$PRIVATE_IP",
#   "acl_datacenter":"us-east-2",
#   "datacenter":"us-east-2",
#   "acl_default_policy":"deny",
#   "acl_down_policy":"deny",
#   "acl_master_token":"${acl_master_token}",
#   "encrypt": "${consul_encrypt}",
#   "disable_remote_exec": true,
#   "disable_update_check": true,
#   "leave_on_terminate": true
# }
# EOF

# # Init a client
# export CONSUL_SERVICE_HOST=internal-consul-elb.wize.mx
# docker run --restart always --network=host -i -v /tmp/consul:/consul/data -v /etc/consul:/etc/consul  -d consul:1.0.1 agent \
#     -data-dir=/tmp/consul \
#     -retry-join=$CONSUL_SERVICE_HOST \
#     -config-file=/etc/consul/server.json \
#     -node=dockerd-$PRIVATE_IP_HIPHENS
#
# # Init a server
# docker run  -ti --net=host -v /etc/consul:/etc/consul consul:1.0.1 agent \
#    -server \
#    -ui \
#    -rejoin \
#    -advertise=$PRIVATE_IP \
#    -config-file=/etc/consul/server.json \
#    -bind=$PRIVATE_IP \
#    -bootstrap-expect=1 \
#    -node=consul-4
