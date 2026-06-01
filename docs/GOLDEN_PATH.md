# Golden Path

This is the expected happy path from a brand-new server to archived k6 run artifacts.

## 1. Prepare A New Server

Connect to the new server as `root` or another user with sudo rights.

Create a small bootstrap directory:

```bash
mkdir -p /opt/jmxtok6-bootstrap
cd /opt/jmxtok6-bootstrap
```

Copy these files to the directory:

```text
scripts/00-zero-server-bootstrap.sh
scripts/00-zero-server.env.example
```

Create the real config:

```bash
cp 00-zero-server.env.example 00-zero-server.env
nano 00-zero-server.env
```

Minimum config:

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

TARGET_SSH_PASSWORD_CREDENTIALS_ID=jmxtok6-server-password

APP_CONTAINER_NAME=jmxtok6changer
APP_IMAGE_NAME=jmxtok6changer
APP_IMAGE_TAG=latest
APP_HOST_PORT=8080
APP_DOCKER_NETWORK=jmxtok6
```

Run bootstrap:

```bash
bash 00-zero-server-bootstrap.sh ./00-zero-server.env
```

Bootstrap creates the Linux user, directories, Docker, Terraform, repository checkout, Jenkins container, Jenkins jobs, and Jenkins credentials.

## 2. Run Jenkins Environment Setup

Open Jenkins:

```text
http://YOUR_SERVER_IP:8081
```

Login:

```text
user: admin
password: value from JENKINS_ADMIN_PASSWORD
```

Run:

```text
jmxtok6/setup-environment
```

Use values from:

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

This job runs Ansible configuration and deploys the service.

## 3. Verify The Service

From a browser:

```text
http://YOUR_SERVER_IP:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

Swagger:

```text
http://YOUR_SERVER_IP:8080/swagger-ui/index.html
```

## 4. Upload JMX

If the JMX has no external files:

```bash
curl -F "file=@test-plan.jmx" \
  http://YOUR_SERVER_IP:8080/api/v1/conversions
```

If the JMX uses CSV Data Set Config or other local files, upload them as `resources`:

```bash
curl -F "file=@test-plan.jmx" \
  -F "resources=@opencart_test_users.csv" \
  http://YOUR_SERVER_IP:8080/api/v1/conversions
```

For multiple files:

```bash
curl -F "file=@test-plan.jmx" \
  -F "resources=@users.csv" \
  -F "resources=@products.csv" \
  http://YOUR_SERVER_IP:8080/api/v1/conversions
```

The response contains:

```json
{
  "conversionId": "CONVERSION_ID",
  "downloadUrl": "/api/v1/conversions/CONVERSION_ID/script",
  "bundleUrl": "/api/v1/conversions/CONVERSION_ID/bundle",
  "resourceCount": 1
}
```

Use a new `conversionId` whenever you change the JMX or support files.

## 5. Run k6 In Jenkins

Open Jenkins and run:

```text
jmxtok6/run-k6
```

Parameters:

```text
CONVERSION_ID=CONVERSION_ID
SERVICE_URL=http://jmxtok6changer:8080
K6_IMAGE=grafana/k6:latest
K6_ARGS=--summary-export summary.json
RESULTS_DIR=k6-results
DOCKER_NETWORK=jmxtok6
JENKINS_CONTAINER_NAME=jmxtok6-jenkins
CLEAN_RESULTS=true
```

The job downloads:

```text
GET /api/v1/conversions/CONVERSION_ID/bundle
```

Then it runs k6 from the unpacked bundle directory, so CSV files are available beside the generated script.

## 6. Download Run Artifacts

Open the completed Jenkins build and download archived artifacts.

Expected artifacts:

```text
generated .k6.js
uploaded resource files, for example CSV
summary.json
k6-output.log
run-metadata.json
```

## 7. Stop Runtime When Needed

To stop and remove the service container:

```text
jmxtok6/destroy
```

To remove Jenkins itself:

```bash
cd /opt/jmxfile/infra/terraform/jenkins
terraform destroy -auto-approve
```

Do not remove the Jenkins volume unless you intentionally want to delete Jenkins history, credentials, and job state.
