param(
    [string]$ConfigPath = "scripts\bootstrap-server.env"
)

$ErrorActionPreference = "Stop"

function Read-BootstrapConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        throw "Config file was not found: $Path. Copy scripts\bootstrap-server.env.example to scripts\bootstrap-server.env first."
    }

    $config = @{}
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            throw "Invalid config line: $line"
        }

        $key = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()
        $config[$key] = $value
    }

    return $config
}

function Require-Config {
    param(
        [hashtable]$Config,
        [string]$Key
    )

    if (-not $Config.ContainsKey($Key) -or $Config[$Key] -eq "") {
        throw "Required config value is missing: $Key"
    }

    return $Config[$Key]
}

function Get-OptionalConfig {
    param(
        [hashtable]$Config,
        [string]$Key,
        [string]$DefaultValue = ""
    )

    if (-not $Config.ContainsKey($Key)) {
        return $DefaultValue
    }

    return $Config[$Key]
}

$config = Read-BootstrapConfig -Path $ConfigPath

$serverHost = Require-Config -Config $config -Key "SERVER_HOST"
$sshUser = Require-Config -Config $config -Key "SSH_USER"
$repoUrl = Require-Config -Config $config -Key "REPO_URL"
$jenkinsAdminPassword = Require-Config -Config $config -Key "JENKINS_ADMIN_PASSWORD"

$sshKeyPath = Get-OptionalConfig -Config $config -Key "SSH_KEY_PATH"
$branch = Get-OptionalConfig -Config $config -Key "BRANCH" -DefaultValue "main"
$remoteProjectDir = Get-OptionalConfig -Config $config -Key "REMOTE_PROJECT_DIR" -DefaultValue "/opt/jmxtok6changer"
$jenkinsAdminUser = Get-OptionalConfig -Config $config -Key "JENKINS_ADMIN_USER" -DefaultValue "admin"
$jenkinsPort = [int](Get-OptionalConfig -Config $config -Key "JENKINS_PORT" -DefaultValue "8081")
$servicePort = [int](Get-OptionalConfig -Config $config -Key "SERVICE_PORT" -DefaultValue "8080")
$jenkinsPublicUrl = Get-OptionalConfig -Config $config -Key "JENKINS_PUBLIC_URL"
$appGitCredentialsId = Get-OptionalConfig -Config $config -Key "APP_GIT_CREDENTIALS_ID"
$skipJenkinsDeployValue = (Get-OptionalConfig -Config $config -Key "SKIP_JENKINS_DEPLOY" -DefaultValue "true").ToLowerInvariant()
$skipJenkinsDeploy = $skipJenkinsDeployValue -in @("1", "true", "yes", "y")

$bootstrapScript = Join-Path $PSScriptRoot "bootstrap-server.ps1"
$arguments = @{
    ServerHost = $serverHost
    SshUser = $sshUser
    RepoUrl = $repoUrl
    Branch = $branch
    JenkinsAdminPassword = $jenkinsAdminPassword
    JenkinsAdminUser = $jenkinsAdminUser
    JenkinsPort = $jenkinsPort
    ServicePort = $servicePort
    RemoteProjectDir = $remoteProjectDir
    JenkinsPublicUrl = $jenkinsPublicUrl
    AppGitCredentialsId = $appGitCredentialsId
}

if ($sshKeyPath -ne "") {
    $arguments["SshKeyPath"] = $sshKeyPath
}

if ($skipJenkinsDeploy) {
    $arguments["SkipJenkinsDeploy"] = $true
}

Write-Host "Starting bootstrap using config: $ConfigPath"
& $bootstrapScript @arguments
