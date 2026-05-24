# Ansible Server Configuration

This directory contains the Ansible layer used by Jenkins to prepare a target Docker host.

## Flow

```text
Terraform creates Jenkins
Jenkins runs jmxtok6/configure-server
Ansible configures the target server
Jenkins runs jmxtok6/deploy
```

## Inventory

Create a private inventory file from the example:

```bash
cp infra/ansible/inventory/server.example.ini infra/ansible/inventory/server.ini
```

Example:

```ini
[jmxtok6]
app-server ansible_host=203.0.113.10 ansible_user=ubuntu
```

`infra/ansible/inventory/server.ini` is ignored by Git because it may contain real server addresses.

## Playbook

The main playbook is:

```text
infra/ansible/playbooks/configure-server.yml
```

It installs and configures:

- Docker
- Git
- Maven
- required OS packages
- artifacts directory
- Docker network used by service and k6 containers
- Docker access for the configured service user

## Run Locally

```bash
ansible-playbook \
  -i infra/ansible/inventory/server.ini \
  infra/ansible/playbooks/configure-server.yml \
  -e "artifacts_dir=/opt/jmxtok6changer/artifacts" \
  -e "docker_network=jmxtok6" \
  -e "service_user=ubuntu"
```

## Run From Jenkins

Use the `jmxtok6/configure-server` job.

Important parameters:

- `ANSIBLE_INVENTORY` - inventory path in the repository
- `SSH_CREDENTIALS_ID` - Jenkins SSH private key credentials id
- `ARTIFACTS_DIR` - host directory mounted by the service container
- `DOCKER_NETWORK` - shared Docker network
- `SERVICE_USER` - Linux user allowed to operate Docker
- `CHECK_MODE` - dry-run mode

For a Docker-based Jenkins controller, do not use `localhost` as the target unless you intentionally want to configure the Jenkins container itself. Use SSH inventory pointing to the Docker host.
