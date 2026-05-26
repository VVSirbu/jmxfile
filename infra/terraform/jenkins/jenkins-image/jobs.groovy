def repoUrl = System.getenv('APP_GIT_REPO_URL')
def branch = System.getenv('APP_GIT_BRANCH') ?: 'main'
def credentialsId = System.getenv('APP_GIT_CREDENTIALS_ID') ?: ''

if (!repoUrl?.trim()) {
    throw new IllegalStateException('APP_GIT_REPO_URL is required')
}

def createPipelineJob = { String jobName, String descriptionText, String pipelineScriptPath ->
    pipelineJob(jobName) {
        description(descriptionText)
        keepDependencies(false)
        properties {
            disableConcurrentBuilds()
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repoUrl)
                            if (credentialsId?.trim()) {
                                credentials(credentialsId)
                            }
                        }
                        branches(branch)
                        extensions {
                            cloneOptions {
                                shallow(true)
                                noTags(false)
                                depth(1)
                                timeout(10)
                            }
                        }
                    }
                }
                scriptPath(pipelineScriptPath)
                lightweight(true)
            }
        }
    }
}

folder('jmxtok6') {
    description('JMX to k6 service delivery and test jobs')
}

createPipelineJob(
        'jmxtok6/configure-server',
        'Configures the Docker host with Ansible before application deployment.',
        'jenkins/configure-server.Jenkinsfile'
)

createPipelineJob(
        'jmxtok6/setup-environment',
        'Orchestrates Terraform validation, Ansible server configuration, and service deployment.',
        'jenkins/setup-environment.Jenkinsfile'
)

createPipelineJob(
        'jmxtok6/deploy',
        'Builds and deploys the JMX to k6 converter service as a Docker container.',
        'jenkins/deploy.Jenkinsfile'
)

createPipelineJob(
        'jmxtok6/run-k6',
        'Downloads a generated k6 script by conversion id and runs it with Docker.',
        'jenkins/run-k6.Jenkinsfile'
)

createPipelineJob(
        'jmxtok6/destroy',
        'Stops and removes the deployed service container and optional resources.',
        'jenkins/destroy.Jenkinsfile'
)
