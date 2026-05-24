provider "docker" {}

locals {
  jenkins_image_full_name = "${var.jenkins_image_name}:${var.jenkins_image_tag}"
}

resource "docker_image" "jenkins" {
  name = local.jenkins_image_full_name

  build {
    context = "${path.module}/jenkins-image"
  }
}

resource "docker_network" "ci" {
  name = var.jenkins_network_name
}

resource "docker_volume" "jenkins_home" {
  name = "${var.jenkins_container_name}-home"
}

resource "docker_container" "jenkins" {
  name     = var.jenkins_container_name
  image    = docker_image.jenkins.image_id
  hostname = var.jenkins_container_name
  restart  = "unless-stopped"
  user     = "root"

  networks_advanced {
    name = docker_network.ci.name
  }

  ports {
    internal = 8080
    external = var.jenkins_http_port
  }

  ports {
    internal = 50000
    external = var.jenkins_agent_port
  }

  volumes {
    volume_name    = docker_volume.jenkins_home.name
    container_path = "/var/jenkins_home"
  }

  volumes {
    host_path      = var.docker_sock_path
    container_path = "/var/run/docker.sock"
  }

  env = [
    "CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/casc.yaml",
    "JAVA_OPTS=-Djenkins.install.runSetupWizard=false",
    "JENKINS_ADMIN_ID=${var.jenkins_admin_user}",
    "JENKINS_ADMIN_PASSWORD=${var.jenkins_admin_password}",
    "JENKINS_PUBLIC_URL=${var.jenkins_public_url}",
    "APP_GIT_REPO_URL=${var.app_git_repo_url}",
    "APP_GIT_BRANCH=${var.app_git_branch}",
    "APP_GIT_CREDENTIALS_ID=${var.app_git_credentials_id}"
  ]

  healthcheck {
    test         = ["CMD-SHELL", "curl -fsS http://localhost:8080/login >/dev/null || exit 1"]
    interval     = "30s"
    timeout      = "5s"
    start_period = "60s"
    retries      = 5
  }
}
