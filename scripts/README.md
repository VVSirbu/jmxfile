# Bootstrap Scripts

These scripts let you start provisioning from your local machine with one config file and one command.

## Files

- `bootstrap-server.env.example` - input parameters template
- `bootstrap-server.env` - your local private config, ignored by Git
- `run-bootstrap.ps1` - reads `bootstrap-server.env` and calls the bootstrap script
- `bootstrap-server.ps1` - connects to the server over SSH and performs the remote bootstrap
- `server-bootstrap.env.example` - server-side input parameters template
- `server-bootstrap.sh` - runs directly on the server from the project directory
- `provision-pack.env.example` - full server provision template for a fresh server
- `provision-pack.sh` - one-command server pack: user, directories, repo, Jenkins, credentials

## What The Bootstrap Does

From your local machine it:

1. checks SSH access to the server
2. uploads a temporary setup script and Terraform variables
3. installs base packages on the server
4. installs Terraform if missing
5. installs and starts Docker
6. clones or updates the project under `REMOTE_PROJECT_DIR`
7. runs `terraform init`, `terraform validate`, and `terraform apply`
8. waits for Jenkins to become reachable
9. optionally triggers the Jenkins deploy job

By default `SKIP_JENKINS_DEPLOY=true`, because on a fresh Jenkins you usually still need to add SSH credentials for the Ansible configure-server job.

## Usage

### Option 0: Brand-New Empty Server

This is the intended staged flow for a server with nothing installed.

Use:

```text
scripts/00-zero-server-bootstrap.sh
scripts/00-zero-server.env.example
```

The script only prepares the server and creates Jenkins. It does not upload JMX files and does not run k6. Those steps stay in the application API and Jenkins jobs.

High-level flow:

```text
empty server
  -> 00-zero-server-bootstrap.sh
  -> base packages, user, dirs, Docker, Terraform
  -> git clone repository
  -> Terraform creates Jenkins
  -> Jenkins creates jobs and credentials
  -> jmxtok6/setup-environment
  -> Ansible configures server
  -> Jenkins deploys service
  -> API converts JMX to k6
  -> jmxtok6/run-k6 runs artifact by conversionId
```

Full guide:

```text
docs/ZERO_SERVER_FULL_CYCLE.md
```

### Recommended: Full Server Provision Pack

Use this on a new empty Ubuntu/Debian server when you want the server to prepare itself from one config file.

Copy the pack files to the server:

```bash
mkdir -p /opt/jmxtok6-pack
cd /opt/jmxtok6-pack
```

Put these files there:

```text
provision-pack.sh
provision-pack.env
```

Create the config from the example:

```bash
cp provision-pack.env.example provision-pack.env
nano provision-pack.env
```

Minimum values to fill:

```text
SERVER_HOST=YOUR_SERVER_IP
SERVICE_USER=jmxtok6
SERVICE_USER_PASSWORD=strong-linux-user-password

PROJECT_DIR=/opt/jmxfile
ARTIFACTS_DIR=/opt/jmxtok6changer/artifacts

APP_GIT_REPO_URL=https://github.com/VVSirbu/jmxfile.git
APP_GIT_BRANCH=main

JENKINS_ADMIN_PASSWORD=strong-jenkins-admin-password
JENKINS_PORT=8081
```

Run:

```bash
bash provision-pack.sh ./provision-pack.env
```

The pack will:

1. install base packages, Docker, SSH server, Git, unzip, curl
2. create `SERVICE_USER`
3. set `SERVICE_USER_PASSWORD`
4. add the user to `sudo` and `docker`
5. create `PROJECT_DIR` and `ARTIFACTS_DIR`
6. clone or update `APP_GIT_REPO_URL`
7. install Terraform if missing
8. write `infra/terraform/jenkins/terraform.tfvars`
9. create Jenkins with Terraform
10. create Jenkins username/password credential for Ansible automatically
11. write `scripts/generated-jenkins-params.env` as a checklist for the Jenkins job

Then open Jenkins:

```text
http://YOUR_SERVER_IP:8081
```

Run:

```text
jmxtok6/setup-environment
```

Use values from:

```text
/opt/jmxfile/scripts/generated-jenkins-params.env
```

The important defaults are:

```text
SSH_AUTH_MODE=password
SSH_PASSWORD_CREDENTIALS_ID=jmxtok6-server-password
TARGET_USER=jmxtok6
SERVICE_USER=jmxtok6
```

After the Jenkins job completes:

```text
Health:  http://YOUR_SERVER_IP:8080/actuator/health
Swagger: http://YOUR_SERVER_IP:8080/swagger-ui/index.html
```

### Option A: Run From Your Local Windows Machine

Copy the example config:

```powershell
Copy-Item scripts\bootstrap-server.env.example scripts\bootstrap-server.env
```

Edit `scripts\bootstrap-server.env`:

```text
SERVER_HOST=YOUR_SERVER_IP
SSH_USER=ubuntu
SSH_KEY_PATH=C:\Users\you\.ssh\id_rsa

REPO_URL=https://github.com/your-org/jmxtok6changer.git
BRANCH=main

JENKINS_ADMIN_PASSWORD=change-me-strong-password
JENKINS_PUBLIC_URL=http://YOUR_SERVER_IP:8081/
```

Run:

```powershell
.\scripts\run-bootstrap.ps1
```

If PowerShell blocks local script execution, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-bootstrap.ps1
```

Or pass a custom config path:

```powershell
.\scripts\run-bootstrap.ps1 -ConfigPath .\my-server.env
```

## After Bootstrap

Open Jenkins:

```text
http://YOUR_SERVER_IP:8081
```

Then continue with:

1. add Jenkins SSH credential for the target server
2. run `jmxtok6/configure-server`
3. run `jmxtok6/deploy`
4. upload JMX through the API
5. run `jmxtok6/run-k6`
6. download Jenkins build artifacts
7. run `jmxtok6/destroy`
8. run `terraform destroy` when Jenkins/infrastructure should be removed

### Option B: Copy Files To The Server And Run There

On the server, put the project in a fixed directory, for example:

```bash
sudo mkdir -p /opt
cd /opt
sudo git clone --branch main https://github.com/your-org/jmxtok6changer.git
sudo chown -R "$USER:$USER" /opt/jmxtok6changer
cd /opt/jmxtok6changer
```

Create the server-side config:

```bash
cp scripts/server-bootstrap.env.example scripts/server-bootstrap.env
nano scripts/server-bootstrap.env
```

Fill in at least:

```text
JENKINS_ADMIN_PASSWORD=change-me-strong-password
JENKINS_PUBLIC_URL=http://YOUR_SERVER_IP:8081/
APP_GIT_REPO_URL=https://github.com/your-org/jmxtok6changer.git
APP_GIT_BRANCH=main
```

Run:

```bash
bash scripts/server-bootstrap.sh
```

Or use a custom config path:

```bash
bash scripts/server-bootstrap.sh /opt/private/jmxtok6-server.env
```

The server-side script installs packages, starts Docker, installs Terraform if needed, writes `infra/terraform/jenkins/terraform.tfvars`, applies only the Jenkins Terraform stack, and waits for Jenkins.

After Jenkins is available, use `jmxtok6/setup-environment` as the main orchestrator. It can validate Terraform files, run Ansible server configuration, and trigger the service deploy job.
