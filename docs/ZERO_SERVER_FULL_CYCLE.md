# Zero Server Full Cycle

This guide describes the intended deployment flow for a brand-new server with nothing preinstalled.

## Target Flow

1. Run one bootstrap script on the empty server.
2. Bootstrap installs base dependencies, creates Linux users and directories, installs Docker and Terraform.
3. Bootstrap clones this repository with Git.
4. Bootstrap uses Terraform to create Jenkins.
5. Jenkins Configuration as Code creates the required jobs and credentials.
6. You run `jmxtok6/setup-environment` in Jenkins.
7. Jenkins runs Ansible to finish server configuration.
8. Jenkins builds and deploys the Spring Boot service as a Docker container.
9. You upload a JMX file through the API endpoint.
10. The service returns a `conversionId` and stores the generated k6 artifact.
11. You run `jmxtok6/run-k6` in Jenkins with that `conversionId`.
12. Jenkins runs k6 and archives run artifacts.

## 1. Copy Bootstrap Files To The Server

On the new server:

```bash
mkdir -p /opt/jmxtok6-bootstrap
cd /opt/jmxtok6-bootstrap
```

Copy these files into that directory:

```text
00-zero-server-bootstrap.sh
00-zero-server.env
```

Create the env file from the template:

```bash
cp 00-zero-server.env.example 00-zero-server.env
nano 00-zero-server.env
```

Minimum values:

```text
SERVER_HOST=YOUR_SERVER_IP
REPO_URL=https://github.com/VVSirbu/jmxfile.git
REPO_BRANCH=main
PROJECT_DIR=/opt/jmxfile

ADMIN_USER=jmxtok6
ADMIN_PASSWORD=strong-linux-password
ARTIFACTS_DIR=/opt/jmxtok6changer/artifacts

JENKINS_ADMIN_USER=admin
JENKINS_ADMIN_PASSWORD=strong-jenkins-password
JENKINS_PORT=8081
```

## 2. Bootstrap The Empty Server

Run:

```bash
bash 00-zero-server-bootstrap.sh ./00-zero-server.env
```

The script does this:

```text
apt packages -> Linux user -> directories -> Docker -> Terraform -> git clone -> Terraform Jenkins -> Jenkins jobs
```

The automation user is configured with passwordless sudo:

```text
/etc/sudoers.d/jmxtok6
```

This is intentional for Jenkins and Ansible automation. Keep this user dedicated to CI/CD tasks.

When it finishes, Jenkins should be available:

```text
http://YOUR_SERVER_IP:8081
```

The script also writes a Jenkins parameter checklist:

```text
/opt/jmxfile/scripts/01-jenkins-setup-params.env
```

Before continuing, verify that the Jenkins container has the required CLIs:

```bash
docker exec jmxtok6-jenkins docker --version
docker exec jmxtok6-jenkins terraform -version
docker exec jmxtok6-jenkins ansible --version
docker exec jmxtok6-jenkins sshpass -V
```

## 3. Run Jenkins Setup Job

Open Jenkins and run:

```text
jmxtok6/setup-environment
```

Use the values from:

```text
/opt/jmxfile/scripts/01-jenkins-setup-params.env
```

Important values:

```text
TARGET_HOST=YOUR_SERVER_IP
TARGET_USER=jmxtok6
SSH_AUTH_MODE=password
SSH_PASSWORD_CREDENTIALS_ID=jmxtok6-server-password
SERVICE_USER=jmxtok6
ARTIFACTS_DIR=/opt/jmxtok6changer/artifacts
DOCKER_NETWORK=jmxtok6
HOST_PORT=8080
```

The Jenkins credential `jmxtok6-server-password` is created automatically by Jenkins Configuration as Code. It uses:

```text
username = ADMIN_USER
password = ADMIN_PASSWORD
```

## 4. Verify Service

After `setup-environment` finishes:

```bash
curl http://YOUR_SERVER_IP:8080/actuator/health
```

Swagger:

```text
http://YOUR_SERVER_IP:8080/swagger-ui/index.html
```

## 5. Upload JMX And Get k6 Artifact

Upload a JMX file:

```bash
curl -F "file=@test-plan.jmx" http://YOUR_SERVER_IP:8080/api/v1/conversions
```

If the JMX uses CSV Data Set Config, upload the CSV together with the JMX:

```bash
curl -F "file=@test-plan.jmx" -F "resources=@opencart_test_users.csv" http://YOUR_SERVER_IP:8080/api/v1/conversions
```

The response contains a `conversionId`.

Download the generated k6 script:

```bash
curl -OJ http://YOUR_SERVER_IP:8080/api/v1/conversions/CONVERSION_ID/script
```

Download the complete k6 bundle with support files:

```bash
curl -OJ http://YOUR_SERVER_IP:8080/api/v1/conversions/CONVERSION_ID/bundle
```

## 6. Run k6 From Jenkins

Run Jenkins job:

```text
jmxtok6/run-k6
```

Set:

```text
CONVERSION_ID=the id returned by POST /api/v1/conversions
SERVICE_BASE_URL=http://jmxtok6changer:8080
DOCKER_NETWORK=jmxtok6
```

Jenkins will archive:

```text
k6 script
summary.json
k6-output.log
run-metadata.json
```

## 7. Destroy Runtime

To stop and remove the service container:

```text
jmxtok6/destroy
```

To remove Jenkins itself:

```bash
cd /opt/jmxfile/infra/terraform/jenkins
terraform destroy -auto-approve
```
