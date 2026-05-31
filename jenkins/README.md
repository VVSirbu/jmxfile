# Jenkins Pipelines

This directory contains three separate Jenkins declarative pipelines:

- `configure-server.Jenkinsfile` - runs Ansible to prepare the target Docker host
- `setup-environment.Jenkinsfile` - orchestrates Terraform validation, Ansible configuration, and service deployment
- `deploy.Jenkinsfile` - builds and deploys the service as a Docker container
- `run-k6.Jenkinsfile` - downloads a generated k6 script by conversion id and runs it
- `destroy.Jenkinsfile` - stops and removes the deployed service resources

These jobs can be created manually in Jenkins, or automatically by the Terraform Jenkins stack in `infra/terraform/jenkins`.

## Assumptions

The Jenkins agent that runs these jobs must have:

- Ansible for the configure job
- Docker CLI access
- Maven access for the deploy job when `RUN_TESTS=true`
- `curl`
- network access to the deployed service

The deploy, run-k6, and destroy pipelines are designed for the common setup where Jenkins can access the target Docker host. The Terraform Jenkins stack mounts the host Docker socket into Jenkins for that purpose.

The configure-server pipeline uses Ansible over SSH. If Jenkins runs in Docker, `localhost` means the Jenkins container, not the host server. Use an inventory file with the real target host and provide an SSH credential id.

## Setup Environment Job

Create a Jenkins Pipeline job that points to:

```text
jenkins/setup-environment.Jenkinsfile
```

This is the recommended entry point after the server bootstrap has created Jenkins. It can:

- validate Terraform files
- run the `jmxtok6/configure-server` Ansible job
- run the `jmxtok6/deploy` service deployment job

Important parameters:

- `RUN_TERRAFORM_VALIDATE` - validate Terraform files before setup
- `RUN_ANSIBLE_CONFIGURE` - run Ansible server configuration
- `RUN_DEPLOY` - deploy the service after configuration
- `ANSIBLE_INVENTORY` - optional inventory file from Git
- `TARGET_HOST` - target server IP or hostname when `ANSIBLE_INVENTORY` is empty
- `TARGET_USER` - SSH user for generated Ansible inventory
- `SSH_CREDENTIALS_ID` - Jenkins SSH private key credential id
- `SERVICE_USER` - Linux user allowed to operate Docker
- `IMAGE_NAME`, `IMAGE_TAG`, `CONTAINER_NAME`, `HOST_PORT`, `ARTIFACTS_DIR`, `DOCKER_NETWORK` - passed to the deploy job

## Configure Server Job

Create a Jenkins Pipeline job that points to:

```text
jenkins/configure-server.Jenkinsfile
```

Important parameters:

- `ANSIBLE_INVENTORY` - optional inventory file from Git
- `TARGET_HOST` - target server IP or hostname when `ANSIBLE_INVENTORY` is empty
- `TARGET_USER` - SSH user for generated Ansible inventory
- `SSH_CREDENTIALS_ID` - Jenkins SSH private key credential id
- `ARTIFACTS_DIR` - host directory for generated scripts
- `DOCKER_NETWORK` - shared Docker network
- `SERVICE_USER` - Linux user allowed to operate Docker
- `CHECK_MODE` - Ansible dry-run mode

## Deploy Job

Create a Jenkins Pipeline job that points to:

```text
jenkins/deploy.Jenkinsfile
```

Important parameters:

- `IMAGE_NAME` - Docker image name, default `jmxtok6changer`
- `IMAGE_TAG` - Docker image tag, default `latest`
- `CONTAINER_NAME` - deployed container name, default `jmxtok6changer`
- `HOST_PORT` - host port mapped to service port `8080`
- `ARTIFACTS_DIR` - persistent host directory mounted to `/app/artifacts`
- `DOCKER_NETWORK` - Docker network shared by the service and optional k6 containers
- `RUN_TESTS` - run `mvn test` before image build
- `RECREATE_CONTAINER` - replace an existing container with the same name

The job validates deployment with:

```text
GET /actuator/health
```

## Run k6 Job

Create a Jenkins Pipeline job that points to:

```text
jenkins/run-k6.Jenkinsfile
```

Important parameters:

- `CONVERSION_ID` - id returned by `POST /api/v1/conversions`
- `SERVICE_URL` - base URL of the converter service, default `http://localhost:8080`
- `K6_IMAGE` - k6 Docker image, default `grafana/k6:latest`
- `K6_ARGS` - extra arguments passed to `k6 run`
- `RESULTS_DIR` - workspace directory for downloaded script and reports
- `DOCKER_NETWORK` - Docker network used by the k6 container

The job downloads:

```text
GET /api/v1/conversions/{CONVERSION_ID}/script
```

Then it runs:

```text
k6 run <downloaded-script>
```

The downloaded script and k6 reports are archived as Jenkins artifacts.

## Destroy Job

Create a Jenkins Pipeline job that points to:

```text
jenkins/destroy.Jenkinsfile
```

Important parameters:

- `CONTAINER_NAME` - container to stop and remove
- `REMOVE_IMAGE` - remove the Docker image too
- `REMOVE_ARTIFACTS` - remove generated scripts from the host artifacts directory
- `REMOVE_NETWORK` - remove the Docker network
- `CONFIRM_DESTROY` - must be set to `DELETE`

The confirmation parameter exists to avoid accidental deletion from a manually started Jenkins job.
