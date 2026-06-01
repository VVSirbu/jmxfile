#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${1:-./provision-pack.env}"

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1
}

run_sudo() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

run_as_service_user() {
  if [ "$(id -un)" = "$SERVICE_USER" ]; then
    "$@"
  else
    sudo -H -u "$SERVICE_USER" "$@"
  fi
}

load_config() {
  if [ ! -f "$CONFIG_PATH" ]; then
    fail "Config file was not found: $CONFIG_PATH. Copy scripts/provision-pack.env.example to provision-pack.env first."
  fi

  set -a
  # shellcheck disable=SC1090
  . "$CONFIG_PATH"
  set +a
}

require_config() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    fail "Required config value is missing: $name"
  fi
}

install_packages() {
  log "Installing base packages"
  if require_command apt-get; then
    run_sudo apt-get update
    run_sudo apt-get install -y ca-certificates curl git sudo unzip docker.io openssh-server
  else
    fail "This provision pack currently supports Debian/Ubuntu servers with apt-get."
  fi
}

create_service_user() {
  log "Preparing service user: $SERVICE_USER"

  if ! id "$SERVICE_USER" >/dev/null 2>&1; then
    run_sudo useradd -m -s "$SERVICE_USER_SHELL" "$SERVICE_USER"
  fi

  echo "$SERVICE_USER:$SERVICE_USER_PASSWORD" | run_sudo chpasswd

  if [ "$SERVICE_USER_SUDO" = "true" ]; then
    run_sudo usermod -aG sudo "$SERVICE_USER"
    printf '%s ALL=(ALL) NOPASSWD:ALL\n' "$SERVICE_USER" | run_sudo tee "/etc/sudoers.d/$SERVICE_USER" >/dev/null
    run_sudo chmod 0440 "/etc/sudoers.d/$SERVICE_USER"
  fi

  run_sudo usermod -aG docker "$SERVICE_USER" || true
}

prepare_directories() {
  log "Preparing directories"
  run_sudo mkdir -p "$BASE_DIR" "$PROJECT_DIR" "$ARTIFACTS_DIR"
  run_sudo chown -R "$SERVICE_USER:$SERVICE_USER" "$PROJECT_DIR" "$ARTIFACTS_DIR"
}

enable_services() {
  log "Starting required services"
  run_sudo systemctl enable --now ssh || true
  run_sudo systemctl enable --now docker
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
  run_sudo install -m 0755 "$tmp_dir/terraform" /usr/local/bin/terraform
  rm -rf "$tmp_dir"
  terraform -version
}

sync_repository() {
  log "Cloning or updating repository"

  if [ -d "$PROJECT_DIR/.git" ]; then
    run_as_service_user git -C "$PROJECT_DIR" fetch origin "$APP_GIT_BRANCH"
    run_as_service_user git -C "$PROJECT_DIR" checkout "$APP_GIT_BRANCH"
    run_as_service_user git -C "$PROJECT_DIR" pull --ff-only origin "$APP_GIT_BRANCH"
  else
    if [ -n "$(find "$PROJECT_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
      fail "PROJECT_DIR exists but is not a Git repository and is not empty: $PROJECT_DIR"
    fi
    run_as_service_user git clone --branch "$APP_GIT_BRANCH" "$APP_GIT_REPO_URL" "$PROJECT_DIR"
  fi

  run_sudo chown -R "$SERVICE_USER:$SERVICE_USER" "$PROJECT_DIR"
}

write_tfvars() {
  local terraform_dir="$PROJECT_DIR/infra/terraform/jenkins"
  local tmp_file
  tmp_file="$(mktemp)"
  log "Writing Terraform variables"

  cat > "$tmp_file" <<EOF
jenkins_admin_user      = "${JENKINS_ADMIN_USER}"
jenkins_admin_password  = "${JENKINS_ADMIN_PASSWORD}"
jenkins_http_port       = ${JENKINS_PORT}
jenkins_public_url      = "${JENKINS_PUBLIC_URL}"

jenkins_container_name  = "${JENKINS_CONTAINER_NAME}"
jenkins_network_name    = "${JENKINS_NETWORK_NAME}"
jenkins_image_name      = "${JENKINS_IMAGE_NAME}"
jenkins_image_tag       = "${JENKINS_IMAGE_TAG}"

app_git_repo_url        = "${APP_GIT_REPO_URL}"
app_git_branch          = "${APP_GIT_BRANCH}"
app_git_credentials_id  = "${APP_GIT_CREDENTIALS_ID}"

target_ssh_password_credentials_id = "${TARGET_SSH_PASSWORD_CREDENTIALS_ID}"
target_ssh_user                    = "${SERVICE_USER}"
target_ssh_password                = "${SERVICE_USER_PASSWORD}"
EOF
  run_sudo install -m 0600 -o "$SERVICE_USER" -g "$SERVICE_USER" "$tmp_file" "$terraform_dir/terraform.tfvars"
  rm -f "$tmp_file"
}

apply_terraform() {
  local terraform_dir="$PROJECT_DIR/infra/terraform/jenkins"
  log "Applying Terraform Jenkins stack"
  run_as_service_user terraform -chdir="$terraform_dir" init
  run_as_service_user terraform -chdir="$terraform_dir" validate
  run_as_service_user terraform -chdir="$terraform_dir" apply -auto-approve
}

wait_for_jenkins() {
  local url="${JENKINS_PUBLIC_URL%/}"
  log "Waiting for Jenkins at $url"

  for attempt in $(seq 1 60); do
    if curl -fsS "$url/login" >/dev/null 2>&1; then
      log "Jenkins is reachable: $url"
      return
    fi
    echo "Waiting for Jenkins, attempt $attempt/60"
    sleep 5
  done

  docker logs "$JENKINS_CONTAINER_NAME" --tail 200 || true
  fail "Jenkins did not become reachable: $url"
}

write_jenkins_params() {
  local params_file="$PROJECT_DIR/scripts/generated-jenkins-params.env"
  local tmp_file
  tmp_file="$(mktemp)"
  log "Writing Jenkins run parameters: $params_file"

  cat > "$tmp_file" <<EOF
RUN_TERRAFORM_VALIDATE=true
RUN_ANSIBLE_CONFIGURE=true
RUN_DEPLOY=true
ANSIBLE_INVENTORY=
TARGET_HOST=${SERVER_HOST}
TARGET_USER=${SERVICE_USER}
SSH_AUTH_MODE=password
SSH_CREDENTIALS_ID=
SSH_PASSWORD_CREDENTIALS_ID=${TARGET_SSH_PASSWORD_CREDENTIALS_ID}
SERVICE_USER=${SERVICE_USER}
IMAGE_NAME=${IMAGE_NAME}
IMAGE_TAG=${IMAGE_TAG}
CONTAINER_NAME=${CONTAINER_NAME}
HOST_PORT=${HOST_PORT}
ARTIFACTS_DIR=${ARTIFACTS_DIR}
DOCKER_NETWORK=${DOCKER_NETWORK}
RUN_TESTS=true
RECREATE_CONTAINER=true
EOF
  run_sudo install -m 0600 -o "$SERVICE_USER" -g "$SERVICE_USER" "$tmp_file" "$params_file"
  rm -f "$tmp_file"
}

print_next_steps() {
  local url="${JENKINS_PUBLIC_URL%/}"
  cat <<EOF

Provision pack completed.

Jenkins:
  $url

Login:
  username: $JENKINS_ADMIN_USER
  password: value from JENKINS_ADMIN_PASSWORD

The service user has been prepared:
  user: $SERVICE_USER
  project dir: $PROJECT_DIR
  artifacts dir: $ARTIFACTS_DIR

Jenkins credential was created automatically:
  id: $TARGET_SSH_PASSWORD_CREDENTIALS_ID
  type: Username with password

Run this Jenkins job next:
  jmxtok6/setup-environment

Use the generated parameter file as a checklist:
  $PROJECT_DIR/scripts/generated-jenkins-params.env

After deployment:
  API: http://$SERVER_HOST:$HOST_PORT
  Health: http://$SERVER_HOST:$HOST_PORT/actuator/health
  Swagger: http://$SERVER_HOST:$HOST_PORT/swagger-ui/index.html
EOF
}

load_config

SERVER_HOST="${SERVER_HOST:-}"
SERVICE_USER="${SERVICE_USER:-jmxtok6}"
SERVICE_USER_PASSWORD="${SERVICE_USER_PASSWORD:-}"
SERVICE_USER_SHELL="${SERVICE_USER_SHELL:-/bin/bash}"
SERVICE_USER_SUDO="${SERVICE_USER_SUDO:-true}"
BASE_DIR="${BASE_DIR:-/opt}"
PROJECT_DIR="${PROJECT_DIR:-/opt/jmxfile}"
ARTIFACTS_DIR="${ARTIFACTS_DIR:-/opt/jmxtok6changer/artifacts}"

APP_GIT_REPO_URL="${APP_GIT_REPO_URL:-}"
APP_GIT_BRANCH="${APP_GIT_BRANCH:-main}"
APP_GIT_CREDENTIALS_ID="${APP_GIT_CREDENTIALS_ID:-}"

JENKINS_ADMIN_USER="${JENKINS_ADMIN_USER:-admin}"
JENKINS_ADMIN_PASSWORD="${JENKINS_ADMIN_PASSWORD:-}"
JENKINS_PORT="${JENKINS_PORT:-8081}"
JENKINS_PUBLIC_URL="${JENKINS_PUBLIC_URL:-http://${SERVER_HOST}:${JENKINS_PORT}/}"
JENKINS_CONTAINER_NAME="${JENKINS_CONTAINER_NAME:-jmxtok6-jenkins}"
JENKINS_NETWORK_NAME="${JENKINS_NETWORK_NAME:-jmxtok6}"
JENKINS_IMAGE_NAME="${JENKINS_IMAGE_NAME:-jmxtok6-jenkins}"
JENKINS_IMAGE_TAG="${JENKINS_IMAGE_TAG:-local}"
TARGET_SSH_PASSWORD_CREDENTIALS_ID="${TARGET_SSH_PASSWORD_CREDENTIALS_ID:-jmxtok6-server-password}"

IMAGE_NAME="${IMAGE_NAME:-jmxtok6changer}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
CONTAINER_NAME="${CONTAINER_NAME:-jmxtok6changer}"
HOST_PORT="${HOST_PORT:-8080}"
DOCKER_NETWORK="${DOCKER_NETWORK:-jmxtok6}"

require_config SERVER_HOST
require_config SERVICE_USER_PASSWORD
require_config APP_GIT_REPO_URL
require_config JENKINS_ADMIN_PASSWORD

install_packages
enable_services
create_service_user
prepare_directories
install_terraform
sync_repository
write_tfvars
apply_terraform
wait_for_jenkins
write_jenkins_params
print_next_steps
