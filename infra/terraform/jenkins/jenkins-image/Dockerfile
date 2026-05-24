FROM jenkins/jenkins:lts-jdk17

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends ansible docker.io curl git maven openssh-client \
    && rm -rf /var/lib/apt/lists/*

USER jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY casc.yaml /usr/share/jenkins/ref/casc.yaml
COPY jobs.groovy /usr/share/jenkins/ref/jobs.groovy

ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/casc.yaml
