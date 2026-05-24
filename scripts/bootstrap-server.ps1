param(
    [Parameter(Mandatory = $true)]
    [string]$ServerHost,

    [Parameter(Mandatory = $true)]
    [string]$SshUser,

    [string]$SshKeyPath = "",

    [Parameter(Mandatory = $true)]
    [string]$RepoUrl,

    [string]$Branch = "main",

    [Parameter(Mandatory = $true)]
    [string]$JenkinsAdminPassword,

    [string]$JenkinsAdminUser = "admin",

    [int]$JenkinsPort = 8081,

    [int]$ServicePort = 8080,

    [string]$RemoteProjectDir = "/opt/jmxtok6changer",

    [string]$JenkinsPublicUrl = "",

    [string]$AppGitCredentialsId = "",

    [switch]$SkipJenkinsDeploy
)

$ErrorActionPreference = "Stop"

function New-TempDirectory {
    $path = Join-Path ([System.IO.Path]::GetTempPath()) ("jmxtok6-bootstrap-" + [System.Guid]::NewGuid())
    New-Item -ItemType Directory -Path $path | Out-Null
    return $path
}

function Get-SshBaseArgs {
    $args = @()
    if ($SshKeyPath -ne "") {
        $args += @("-i", $SshKeyPath)
    }
    $args += @("-o", "StrictHostKeyChecking=accept-new")
    return $args
}

function Invoke-Remote {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command
    )

    $sshArgs = Get-SshBaseArgs
    & ssh @sshArgs "$SshUser@$ServerHost" $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed with exit code $LASTEXITCODE"
    }
}

function Copy-ToRemote {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,

        [Parameter(Mandatory = $true)]
        [string]$Target
    )

    $scpArgs = Get-SshBaseArgs
    & scp @scpArgs $Source "$SshUser@$ServerHost`:$Target"
    if ($LASTEXITCODE -ne 0) {
        throw "SCP failed with exit code $LASTEXITCODE"
    }
}

function Invoke-JenkinsBuild {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JenkinsUrl
    )

    $base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${JenkinsAdminUser}:${JenkinsAdminPassword}"))
    $headers = @{ Authorization = "Basic $base64Auth" }

    Write-Host "Requesting Jenkins crumb..."
    $crumb = Invoke-RestMethod -Headers $headers -Uri "$JenkinsUrl/crumbIssuer/api/json"
    $headers[$crumb.crumbRequestField] = $crumb.crumb

    $deployUrl = "$JenkinsUrl/job/jmxtok6/job/deploy/buildWithParameters"
    $body = @{
        IMAGE_NAME = "jmxtok6changer"
        IMAGE_TAG = "latest"
        CONTAINER_NAME = "jmxtok6changer"
        HOST_PORT = "$ServicePort"
        ARTIFACTS_DIR = "/opt/jmxtok6changer/artifacts"
        DOCKER_NETWORK = "jmxtok6"
        RUN_TESTS = "true"
        RECREATE_CONTAINER = "true"
    }

    Write-Host "Triggering Jenkins deploy job..."
    Invoke-WebRequest -Method Post -Headers $headers -Uri $deployUrl -Body $body | Out-Null
}

if ($JenkinsPublicUrl -eq "") {
    $JenkinsPublicUrl = "http://${ServerHost}:${JenkinsPort}/"
}

$sshArgsPreview = if ($SshKeyPath -ne "") { "-i $SshKeyPath " } else { "" }
Write-Host "Checking SSH access: ssh $sshArgsPreview$SshUser@$ServerHost"
Invoke-Remote "echo connected"

$tempDir = New-TempDirectory
try {
    $remoteSetupPath = "/tmp/jmxtok6-remote-setup.sh"
    $tfvarsRemotePath = "/tmp/jmxtok6-terraform.tfvars"

    $remoteSetup = @'
#!/usr/bin/env bash
set -euo pipefail

REPO_URL="$1"
BRANCH="$2"
REMOTE_PROJECT_DIR="$3"
TERRAFORM_TFVARS_PATH="$4"

install_packages() {
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl git unzip docker.io
  sudo systemctl enable --now docker
}

install_terraform() {
  if command -v terraform >/dev/null 2>&1; then
    terraform -version
    return
  fi

  terraform_version="1.8.5"
  tmp_dir="$(mktemp -d)"
  curl -fsSLo "$tmp_dir/terraform.zip" "https://releases.hashicorp.com/terraform/${terraform_version}/terraform_${terraform_version}_linux_amd64.zip"
  unzip -q "$tmp_dir/terraform.zip" -d "$tmp_dir"
  sudo install -m 0755 "$tmp_dir/terraform" /usr/local/bin/terraform
  rm -rf "$tmp_dir"
  terraform -version
}

checkout_repo() {
  sudo mkdir -p "$(dirname "$REMOTE_PROJECT_DIR")"
  if [ -d "$REMOTE_PROJECT_DIR/.git" ]; then
    sudo git -C "$REMOTE_PROJECT_DIR" fetch --all --prune
    sudo git -C "$REMOTE_PROJECT_DIR" checkout "$BRANCH"
    sudo git -C "$REMOTE_PROJECT_DIR" pull --ff-only origin "$BRANCH"
  else
    sudo rm -rf "$REMOTE_PROJECT_DIR"
    sudo git clone --branch "$BRANCH" "$REPO_URL" "$REMOTE_PROJECT_DIR"
  fi
  sudo chown -R "$USER:$USER" "$REMOTE_PROJECT_DIR"
}

apply_terraform() {
  cd "$REMOTE_PROJECT_DIR/infra/terraform/jenkins"
  cp "$TERRAFORM_TFVARS_PATH" terraform.tfvars
  terraform init
  terraform validate
  terraform apply -auto-approve
}

install_packages
install_terraform
checkout_repo
apply_terraform
'@

    $remoteSetupLocalPath = Join-Path $tempDir "remote-setup.sh"
    Set-Content -Path $remoteSetupLocalPath -Value $remoteSetup -NoNewline -Encoding ascii

    $tfvars = @"
jenkins_admin_user     = "$JenkinsAdminUser"
jenkins_admin_password = "$JenkinsAdminPassword"
jenkins_http_port      = $JenkinsPort
jenkins_public_url     = "$JenkinsPublicUrl"

app_git_repo_url        = "$RepoUrl"
app_git_branch          = "$Branch"
app_git_credentials_id  = "$AppGitCredentialsId"
"@

    $tfvarsLocalPath = Join-Path $tempDir "terraform.tfvars"
    Set-Content -Path $tfvarsLocalPath -Value $tfvars -NoNewline -Encoding ascii

    Write-Host "Uploading bootstrap files..."
    Copy-ToRemote -Source $remoteSetupLocalPath -Target $remoteSetupPath
    Copy-ToRemote -Source $tfvarsLocalPath -Target $tfvarsRemotePath
    Invoke-Remote "chmod +x $remoteSetupPath"

    Write-Host "Installing prerequisites, cloning repo, and applying Terraform on the server..."
    Invoke-Remote "bash $remoteSetupPath '$RepoUrl' '$Branch' '$RemoteProjectDir' '$tfvarsRemotePath'"

    $jenkinsUrl = $JenkinsPublicUrl.TrimEnd("/")
    Write-Host "Waiting for Jenkins at $jenkinsUrl ..."
    $isReady = $false
    for ($i = 1; $i -le 60; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "$jenkinsUrl/login" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                $isReady = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 5
        }
        Write-Host "Waiting for Jenkins, attempt $i/60"
    }

    if (-not $isReady) {
        throw "Jenkins did not become reachable at $jenkinsUrl"
    }

    if (-not $SkipJenkinsDeploy) {
        Invoke-JenkinsBuild -JenkinsUrl $jenkinsUrl
    }

    Write-Host ""
    Write-Host "Bootstrap completed."
    Write-Host "Jenkins: $jenkinsUrl"
    Write-Host "Service URL after deploy: http://${ServerHost}:${ServicePort}"
    Write-Host "Jobs:"
    Write-Host "  $jenkinsUrl/job/jmxtok6/job/deploy/"
    Write-Host "  $jenkinsUrl/job/jmxtok6/job/run-k6/"
    Write-Host "  $jenkinsUrl/job/jmxtok6/job/destroy/"
} finally {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}
