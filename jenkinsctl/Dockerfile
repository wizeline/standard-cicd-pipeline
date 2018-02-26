FROM python:3-alpine
LABEL maintainer="devops@wizeline.com"

USER root
WORKDIR /app

RUN apk add --no-cache \
      zlib \
      zlib-dev \
      libssl1.0 \
      openssl-dev \
      curl \
    && apk add --no-cache --virtual .build-deps \
      build-base \
      musl-dev \
      libffi-dev \
      python3-dev

COPY . /app

RUN pip3 install -r requirements-common.txt \
    && pip3 install -r requirements-dev.txt \
    && pip3 install awscli --upgrade --user \
    && mkdir -p /root/.aws /root/.kube \
    && cp ./docker/* /app

COPY ./aws_credentials /root/.aws/credentials
RUN python3 setup.py install \
    && ./build_install.sh \
    # && ./run_tests.sh \
    && ./publish_install.sh \
    && rm -rf /root/.aws/credentials

ENTRYPOINT ["jenkinsctl"]
CMD [""]
