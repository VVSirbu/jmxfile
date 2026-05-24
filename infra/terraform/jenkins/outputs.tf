output "jenkins_url" {
  description = "Jenkins URL."
  value       = "http://localhost:${var.jenkins_http_port}"
}

output "jenkins_container_name" {
  description = "Jenkins container name."
  value       = docker_container.jenkins.name
}

output "jenkins_network_name" {
  description = "Docker network shared by Jenkins jobs."
  value       = docker_network.ci.name
}

output "created_jobs" {
  description = "Pipeline jobs created by Jenkins Configuration as Code and Job DSL."
  value = [
    "jmxtok6/configure-server",
    "jmxtok6/deploy",
    "jmxtok6/run-k6",
    "jmxtok6/destroy"
  ]
}
