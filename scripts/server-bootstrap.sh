#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${1:-scripts/server-bootstrap.env}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERRAFORM_DIR="$PROJECT_ROOT/infra/terraform/jenkins"

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

load_config() {
  if [ ! -f "$CONFIG_PATH" ]; then
    fail "Config file was not found: $CONFIG_PATH. Copy scripts/server-bootstrap.env.example to scripts/server-bootstrap.env first."
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
    sudo apt-get update
    sudo apt-get install -y ca-certificates curl git unzip docker.io
  else
    fail "This bootstrap currently supports Debian/Ubuntu servers with apt-get."
  fi
}

enable_docker() {
  log "Starting Docker"
  sudo systemctl enable --now docker

  if ! groups "$USER" | grep -qw docker; then
    log "Adding current user to docker group"
    sudo usermod -aG docker "$USER"
    echo "The current user was added to the docker group."
    echo "If Docker commands fail without sudo, log out and log in again, then rerun this script."
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
  sudo install -m 0755 "$tmp_dir/terraform" /usr/local/bin/terraform
  rm -rf "$tmp_dir"
  terraform -version
}

write_tfvars() {
  log "Writing Terraform variables"
  cat > "$TERRAFORM_DIR/terraform.tfvars" <<EOF
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
EOF
}

apply_terraform() {
  log "Applying Terraform Jenkins stack"
  cd "$TERRAFORM_DIR"
  terraform init
  terraform validate
  terraform apply -auto-approve
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

print_next_steps() {
  local url="${JENKINS_PUBLIC_URL%/}"
  cat <<EOF

Bootstrap completed.

Jenkins:
  $url

Login:
  username: $JENKINS_ADMIN_USER
  password: value from JENKINS_ADMIN_PASSWORD

Next steps:
  1. Add Jenkins SSH credential for the target server if you plan to run Ansible over SSH.
  2. Run Jenkins job: jmxtok6/setup-environment
     It can orchestrate Terraform validation, Ansible configuration, and service deployment.
  4. Send JMX to: http://YOUR_SERVER_IP:8080/api/v1/conversions
  5. Run Jenkins job: jmxtok6/run-k6 with CONVERSION_ID.
  6. Download k6 artifacts from the Jenkins build page.
EOF
}

load_config

JENKINS_ADMIN_USER="${JENKINS_ADMIN_USER:-admin}"
JENKINS_PORT="${JENKINS_PORT:-8081}"
APP_GIT_BRANCH="${APP_GIT_BRANCH:-main}"
APP_GIT_CREDENTIALS_ID="${APP_GIT_CREDENTIALS_ID:-}"
JENKINS_CONTAINER_NAME="${JENKINS_CONTAINER_NAME:-jmxtok6-jenkins}"
JENKINS_NETWORK_NAME="${JENKINS_NETWORK_NAME:-jmxtok6}"
JENKINS_IMAGE_NAME="${JENKINS_IMAGE_NAME:-jmxtok6-jenkins}"
JENKINS_IMAGE_TAG="${JENKINS_IMAGE_TAG:-local}"

require_config JENKINS_ADMIN_PASSWORD
require_config JENKINS_PUBLIC_URL
require_config APP_GIT_REPO_URL

install_packages
enable_docker
install_terraform
write_tfvars
apply_terraform
wait_for_jenkins
print_next_steps
