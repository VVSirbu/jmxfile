#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${1:-./00-zero-server.env}"

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_config() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    fail "Required config value is missing: $name"
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1
}

sudo_run() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

as_admin_user() {
  if [ "$(id -un)" = "$ADMIN_USER" ]; then
    "$@"
  else
    sudo -H -u "$ADMIN_USER" "$@"
  fi
}

load_config() {
  if [ ! -f "$CONFIG_PATH" ]; then
    fail "Config file not found: $CONFIG_PATH"
  fi

  set -a
  # shellcheck disable=SC1090
  . "$CONFIG_PATH"
  set +a
}

install_base_dependencies() {
  log "Installing base dependencies"

  if ! require_command apt-get; then
    fail "Only Debian/Ubuntu servers with apt-get are supported by this bootstrap script."
  fi

  sudo_run apt-get update
  sudo_run apt-get install -y ca-certificates curl git openssh-server sudo unzip docker.io
}

create_admin_user() {
  log "Creating admin/service user: $ADMIN_USER"

  if ! id "$ADMIN_USER" >/dev/null 2>&1; then
    sudo_run useradd -m -s /bin/bash "$ADMIN_USER"
  fi

  echo "$ADMIN_USER:$ADMIN_PASSWORD" | sudo_run chpasswd
  sudo_run usermod -aG sudo "$ADMIN_USER"
  sudo_run usermod -aG docker "$ADMIN_USER" || true

  printf '%s ALL=(ALL) NOPASSWD:ALL\n' "$ADMIN_USER" | sudo_run tee "/etc/sudoers.d/$ADMIN_USER" >/dev/null
  sudo_run chmod 0440 "/etc/sudoers.d/$ADMIN_USER"
}

prepare_directories() {
  log "Preparing directories"

  sudo_run mkdir -p "$PROJECT_DIR" "$ARTIFACTS_DIR"
  sudo_run chown -R "$ADMIN_USER:$ADMIN_USER" "$PROJECT_DIR" "$ARTIFACTS_DIR"
}

enable_services() {
  log "Enabling SSH and Docker"

  sudo_run systemctl enable --now ssh || true
  sudo_run systemctl enable --now docker
}

verify_admin_docker_access() {
  log "Checking Docker access for $ADMIN_USER"

  if ! as_admin_user docker info >/dev/null 2>&1; then
    echo "$ADMIN_USER cannot access Docker in the current non-login session yet."
    echo "Terraform will run with sudo/root for this bootstrap run."
    echo "Future SSH sessions for $ADMIN_USER should have docker group access."
  fi
}

install_terraform() {
  if require_command terraform; then
    log "Terraform already installed: $(terraform -version | head -n 1)"
    return
  fi

  log "Installing Terraform"

  local terraform_version="1.8.5"
  local tmp_dir
  tmp_dir="$(mktemp -d)"

  curl -fsSLo "$tmp_dir/terraform.zip" "https://releases.hashicorp.com/terraform/${terraform_version}/terraform_${terraform_version}_linux_amd64.zip"
  unzip -q "$tmp_dir/terraform.zip" -d "$tmp_dir"
  sudo_run install -m 0755 "$tmp_dir/terraform" /usr/local/bin/terraform
  rm -rf "$tmp_dir"
}

clone_or_update_repo() {
  log "Cloning or updating repository"

  if [ -d "$PROJECT_DIR/.git" ]; then
    as_admin_user git -C "$PROJECT_DIR" fetch origin "$REPO_BRANCH"
    as_admin_user git -C "$PROJECT_DIR" checkout "$REPO_BRANCH"
    as_admin_user git -C "$PROJECT_DIR" pull --ff-only origin "$REPO_BRANCH"
    return
  fi

  if [ -n "$(find "$PROJECT_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
    fail "PROJECT_DIR exists but is not empty and is not a Git repository: $PROJECT_DIR"
  fi

  as_admin_user git clone --branch "$REPO_BRANCH" "$REPO_URL" "$PROJECT_DIR"
}

write_terraform_vars() {
  log "Writing Jenkins Terraform variables"

  local terraform_dir="$PROJECT_DIR/infra/terraform/jenkins"
  local tmp_file
  tmp_file="$(mktemp)"

  cat > "$tmp_file" <<EOF
jenkins_admin_user      = "${JENKINS_ADMIN_USER}"
jenkins_admin_password  = "${JENKINS_ADMIN_PASSWORD}"
jenkins_http_port       = ${JENKINS_PORT}
jenkins_public_url      = "http://${SERVER_HOST}:${JENKINS_PORT}/"

jenkins_container_name  = "${JENKINS_CONTAINER_NAME}"
jenkins_network_name    = "${JENKINS_NETWORK_NAME}"
jenkins_image_name      = "${JENKINS_IMAGE_NAME}"
jenkins_image_tag       = "${JENKINS_IMAGE_TAG}"

app_git_repo_url        = "${REPO_URL}"
app_git_branch          = "${REPO_BRANCH}"
app_git_credentials_id  = ""

target_ssh_password_credentials_id = "${TARGET_SSH_PASSWORD_CREDENTIALS_ID}"
target_ssh_user                    = "${ADMIN_USER}"
target_ssh_password                = "${ADMIN_PASSWORD}"
EOF

  sudo_run install -m 0600 -o "$ADMIN_USER" -g "$ADMIN_USER" "$tmp_file" "$terraform_dir/terraform.tfvars"
  rm -f "$tmp_file"
}

apply_jenkins_terraform() {
  log "Creating Jenkins with Terraform"

  local terraform_dir="$PROJECT_DIR/infra/terraform/jenkins"

  sudo_run terraform -chdir="$terraform_dir" init
  sudo_run terraform -chdir="$terraform_dir" validate
  sudo_run terraform -chdir="$terraform_dir" apply -auto-approve
  sudo_run chown -R "$ADMIN_USER:$ADMIN_USER" "$terraform_dir"
}

wait_for_jenkins() {
  local url="http://${SERVER_HOST}:${JENKINS_PORT}"

  log "Waiting for Jenkins: $url"

  for attempt in $(seq 1 60); do
    if curl -fsS "$url/login" >/dev/null 2>&1; then
      log "Jenkins is ready: $url"
      return
    fi

    echo "Waiting for Jenkins, attempt $attempt/60"
    sleep 5
  done

  docker logs "$JENKINS_CONTAINER_NAME" --tail 200 || true
  fail "Jenkins did not become reachable: $url"
}

verify_jenkins_tools() {
  log "Checking Jenkins container tools"

  docker exec "$JENKINS_CONTAINER_NAME" docker --version
  docker exec "$JENKINS_CONTAINER_NAME" terraform -version
  docker exec "$JENKINS_CONTAINER_NAME" ansible --version
  docker exec "$JENKINS_CONTAINER_NAME" sshpass -V
}

write_next_jenkins_params() {
  log "Writing Jenkins setup-environment parameters"

  local tmp_file
  tmp_file="$(mktemp)"

  cat > "$tmp_file" <<EOF
RUN_TERRAFORM_VALIDATE=true
RUN_ANSIBLE_CONFIGURE=true
RUN_DEPLOY=true
ANSIBLE_INVENTORY=
TARGET_HOST=${SERVER_HOST}
TARGET_USER=${ADMIN_USER}
SSH_AUTH_MODE=password
SSH_CREDENTIALS_ID=
SSH_PASSWORD_CREDENTIALS_ID=${TARGET_SSH_PASSWORD_CREDENTIALS_ID}
SERVICE_USER=${ADMIN_USER}
IMAGE_NAME=${APP_IMAGE_NAME}
IMAGE_TAG=${APP_IMAGE_TAG}
CONTAINER_NAME=${APP_CONTAINER_NAME}
HOST_PORT=${APP_HOST_PORT}
ARTIFACTS_DIR=${ARTIFACTS_DIR}
DOCKER_NETWORK=${APP_DOCKER_NETWORK}
RUN_TESTS=true
RECREATE_CONTAINER=true
EOF

  sudo_run install -m 0600 -o "$ADMIN_USER" -g "$ADMIN_USER" "$tmp_file" "$PROJECT_DIR/scripts/01-jenkins-setup-params.env"
  rm -f "$tmp_file"
}

print_result() {
  cat <<EOF

Zero-server bootstrap completed.

Created/prepared:
  Linux user: $ADMIN_USER
  Project dir: $PROJECT_DIR
  Artifacts dir: $ARTIFACTS_DIR
  Jenkins: http://${SERVER_HOST}:${JENKINS_PORT}

Jenkins login:
  user: $JENKINS_ADMIN_USER
  password: value from JENKINS_ADMIN_PASSWORD

Jenkins credential created automatically:
  id: $TARGET_SSH_PASSWORD_CREDENTIALS_ID
  type: Username with password
  username: $ADMIN_USER

Next manual step:
  Open Jenkins and run job: jmxtok6/setup-environment

Use this generated file as the parameter checklist:
  $PROJECT_DIR/scripts/01-jenkins-setup-params.env

After setup-environment:
  Health:  http://${SERVER_HOST}:${APP_HOST_PORT}/actuator/health
  Swagger: http://${SERVER_HOST}:${APP_HOST_PORT}/swagger-ui/index.html

Then:
  1. Upload a JMX file to POST /api/v1/conversions
  2. Copy conversionId from the response
  3. Run Jenkins job jmxtok6/run-k6 with CONVERSION_ID
  4. Download k6 run artifacts from the Jenkins build
EOF
}

load_config

SERVER_HOST="${SERVER_HOST:-}"
REPO_URL="${REPO_URL:-}"
REPO_BRANCH="${REPO_BRANCH:-main}"
PROJECT_DIR="${PROJECT_DIR:-/opt/jmxfile}"
ADMIN_USER="${ADMIN_USER:-jmxtok6}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
ARTIFACTS_DIR="${ARTIFACTS_DIR:-/opt/jmxtok6changer/artifacts}"
JENKINS_ADMIN_USER="${JENKINS_ADMIN_USER:-admin}"
JENKINS_ADMIN_PASSWORD="${JENKINS_ADMIN_PASSWORD:-}"
JENKINS_PORT="${JENKINS_PORT:-8081}"
JENKINS_CONTAINER_NAME="${JENKINS_CONTAINER_NAME:-jmxtok6-jenkins}"
JENKINS_NETWORK_NAME="${JENKINS_NETWORK_NAME:-jmxtok6}"
JENKINS_IMAGE_NAME="${JENKINS_IMAGE_NAME:-jmxtok6-jenkins}"
JENKINS_IMAGE_TAG="${JENKINS_IMAGE_TAG:-local}"
TARGET_SSH_PASSWORD_CREDENTIALS_ID="${TARGET_SSH_PASSWORD_CREDENTIALS_ID:-jmxtok6-server-password}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-jmxtok6changer}"
APP_IMAGE_NAME="${APP_IMAGE_NAME:-jmxtok6changer}"
APP_IMAGE_TAG="${APP_IMAGE_TAG:-latest}"
APP_HOST_PORT="${APP_HOST_PORT:-8080}"
APP_DOCKER_NETWORK="${APP_DOCKER_NETWORK:-jmxtok6}"

require_config SERVER_HOST
require_config REPO_URL
require_config ADMIN_PASSWORD
require_config JENKINS_ADMIN_PASSWORD

install_base_dependencies
enable_services
create_admin_user
prepare_directories
verify_admin_docker_access
install_terraform
clone_or_update_repo
write_terraform_vars
apply_jenkins_terraform
wait_for_jenkins
verify_jenkins_tools
write_next_jenkins_params
print_result
