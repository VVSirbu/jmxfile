FROM jenkins/jenkins:lts-jdk17

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends ansible docker.io curl git maven openssh-client sshpass unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSLo /tmp/terraform.zip https://releases.hashicorp.com/terraform/1.8.5/terraform_1.8.5_linux_amd64.zip \
    && unzip -q /tmp/terraform.zip -d /tmp \
    && install -m 0755 /tmp/terraform /usr/local/bin/terraform \
    && rm -f /tmp/terraform.zip /tmp/terraform

USER jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY casc.yaml /usr/share/jenkins/ref/casc.yaml
COPY jobs.groovy /usr/share/jenkins/ref/jobs.groovy

ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/casc.yaml
