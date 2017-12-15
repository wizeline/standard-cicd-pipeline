FROM jenkinsci/jnlp-slave

ENTRYPOINT ["jenkins-slave"]

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
		python3 \
		python3-pip \
    build-essential \
    libssl-dev \
    libffi-dev \
    python-dev \
    dnsutils \
    iputils-ping \
	&& rm -rf /var/lib/apt/lists/*

RUN curl -o /usr/bin/docker https://master.dockerproject.org/linux/x86_64/docker && \
    chmod +x /usr/bin/docker

# return user to jenkins
USER jenkins
