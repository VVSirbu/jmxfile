variable "jenkins_image_name" {
  description = "Local Jenkins Docker image name built by Terraform."
  type        = string
  default     = "jmxtok6-jenkins"
}

variable "jenkins_image_tag" {
  description = "Local Jenkins Docker image tag built by Terraform."
  type        = string
  default     = "local"
}

variable "jenkins_container_name" {
  description = "Jenkins Docker container name."
  type        = string
  default     = "jmxtok6-jenkins"
}

variable "jenkins_network_name" {
  description = "Docker network shared by Jenkins, the service, and k6 runs."
  type        = string
  default     = "jmxtok6"
}

variable "jenkins_http_port" {
  description = "Host port mapped to Jenkins HTTP port 8080."
  type        = number
  default     = 8081
}

variable "jenkins_agent_port" {
  description = "Host port mapped to Jenkins inbound agent port 50000."
  type        = number
  default     = 50000
}

variable "jenkins_admin_user" {
  description = "Initial Jenkins admin username."
  type        = string
  default     = "admin"
}

variable "jenkins_admin_password" {
  description = "Initial Jenkins admin password."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.jenkins_admin_password) >= 8
    error_message = "jenkins_admin_password must contain at least 8 characters."
  }
}

variable "jenkins_public_url" {
  description = "External Jenkins URL used in Jenkins location config."
  type        = string
  default     = "http://localhost:8081/"
}

variable "app_git_repo_url" {
  description = "Git repository URL that contains the application Jenkinsfiles."
  type        = string

  validation {
    condition     = length(trimspace(var.app_git_repo_url)) > 0
    error_message = "app_git_repo_url is required so Jenkins can load pipeline definitions from SCM."
  }
}

variable "app_git_branch" {
  description = "Git branch used by generated Jenkins jobs."
  type        = string
  default     = "main"
}

variable "app_git_credentials_id" {
  description = "Optional Jenkins credentials id for private Git repositories. Leave empty for public repositories."
  type        = string
  default     = ""
}

variable "docker_sock_path" {
  description = "Host Docker socket path mounted into Jenkins."
  type        = string
  default     = "/var/run/docker.sock"
}
