# Terraform Jenkins Stack

This Terraform module provisions a self-hosted Jenkins stack for the JMX to k6 project.

It creates:

- a custom Jenkins Docker image
- a Jenkins Docker container
- a persistent Jenkins home volume
- a Docker network shared with application and k6 containers
- Jenkins Configuration as Code setup
- Jenkins jobs under the `jmxtok6` folder:
  - `jmxtok6/configure-server`
  - `jmxtok6/deploy`
  - `jmxtok6/run-k6`
  - `jmxtok6/destroy`

## Architecture

Terraform is responsible for the CI/CD platform:

```text
Terraform -> Docker -> Jenkins -> Pipeline jobs
```

Jenkins is responsible for application operations:

```text
configure-server job -> run Ansible against the target server
deploy job  -> build image -> run service container -> health check
run-k6 job  -> download generated script by CONVERSION_ID -> run k6
destroy job -> stop and remove service resources
```

## Requirements

Run Terraform on a Linux server or VM with:

- Terraform 1.6+
- Docker Engine
- outbound internet access for Docker images and Jenkins plugins
- access to the Git repository URL configured in `app_git_repo_url`

The Jenkins container mounts `/var/run/docker.sock`, so Jenkins jobs can run Docker commands on the host.

The Jenkins image also includes Ansible and OpenSSH client so the `configure-server` job can prepare a target server over SSH.

## Usage

Copy the example variables file:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

```hcl
jenkins_admin_password = "replace-with-a-strong-password"
jenkins_public_url     = "http://your-server:8081/"

app_git_repo_url = "https://github.com/your-org/jmxtok6changer.git"
app_git_branch   = "main"
```

Initialize and apply:

```bash
terraform init
terraform apply
```

Open Jenkins:

```text
http://localhost:8081
```

Login with:

```text
username: admin
password: value from jenkins_admin_password
```

## Private Git Repositories

For private repositories, create Jenkins credentials after the first startup and set:

```hcl
app_git_credentials_id = "your-credentials-id"
```

Then run:

```bash
terraform apply
```

## Destroy

To remove the Jenkins platform:

```bash
terraform destroy
```

This removes the Jenkins container, image, network, and Jenkins home volume managed by Terraform.

The application service container is intentionally managed by Jenkins jobs, not this Terraform module. Use the `jmxtok6/destroy` Jenkins job for the deployed service.

## Notes

The initial admin password is passed to Jenkins via Terraform variables and Docker environment. Treat local Terraform state as sensitive.

This module is intentionally provider-neutral: it does not create cloud VMs, firewalls, DNS, or TLS. Those can be added later as a separate cloud-specific Terraform layer once the target platform is known.
