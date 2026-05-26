pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_TERRAFORM_VALIDATE', defaultValue: true, description: 'Validate Terraform files used to provision Jenkins')
        booleanParam(name: 'RUN_ANSIBLE_CONFIGURE', defaultValue: true, description: 'Run Ansible server configuration')
        booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: 'Deploy the service after server configuration')

        string(name: 'ANSIBLE_INVENTORY', defaultValue: 'infra/ansible/inventory/server.example.ini', description: 'Inventory file used by ansible-playbook')
        string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins SSH private key credentials id for the target server')
        string(name: 'SERVICE_USER', defaultValue: 'ubuntu', description: 'Linux user allowed to operate Docker on the target server')

        string(name: 'IMAGE_NAME', defaultValue: 'jmxtok6changer', description: 'Docker image name to build')
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image tag to deploy')
        string(name: 'CONTAINER_NAME', defaultValue: 'jmxtok6changer', description: 'Docker container name')
        string(name: 'HOST_PORT', defaultValue: '8080', description: 'Host port exposed by the service')
        string(name: 'ARTIFACTS_DIR', defaultValue: '/opt/jmxtok6changer/artifacts', description: 'Host directory mounted to /app/artifacts')
        string(name: 'DOCKER_NETWORK', defaultValue: 'jmxtok6', description: 'Docker network for the service and k6 runner')
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run Maven tests before building the image')
        booleanParam(name: 'RECREATE_CONTAINER', defaultValue: true, description: 'Stop and remove an existing container before deploy')
    }

    stages {
        stage('Validate Inputs') {
            steps {
                sh '''
                    set -eu
                    test -n "$ARTIFACTS_DIR"
                    test -n "$DOCKER_NETWORK"
                    test -n "$IMAGE_NAME"
                    test -n "$IMAGE_TAG"
                    test -n "$CONTAINER_NAME"
                    test -n "$HOST_PORT"
                '''
            }
        }

        stage('Terraform Validate') {
            when {
                expression { return params.RUN_TERRAFORM_VALIDATE }
            }
            steps {
                sh '''
                    set -eu
                    terraform -chdir=infra/terraform/jenkins init -backend=false
                    terraform -chdir=infra/terraform/jenkins validate
                '''
            }
        }

        stage('Configure Server With Ansible') {
            when {
                expression { return params.RUN_ANSIBLE_CONFIGURE }
            }
            steps {
                build job: 'jmxtok6/configure-server',
                        wait: true,
                        parameters: [
                                string(name: 'ANSIBLE_INVENTORY', value: params.ANSIBLE_INVENTORY),
                                string(name: 'ANSIBLE_PLAYBOOK', value: 'infra/ansible/playbooks/configure-server.yml'),
                                string(name: 'SSH_CREDENTIALS_ID', value: params.SSH_CREDENTIALS_ID),
                                string(name: 'ARTIFACTS_DIR', value: params.ARTIFACTS_DIR),
                                string(name: 'DOCKER_NETWORK', value: params.DOCKER_NETWORK),
                                string(name: 'SERVICE_USER', value: params.SERVICE_USER),
                                booleanParam(name: 'CHECK_MODE', value: false)
                        ]
            }
        }

        stage('Deploy Service') {
            when {
                expression { return params.RUN_DEPLOY }
            }
            steps {
                build job: 'jmxtok6/deploy',
                        wait: true,
                        parameters: [
                                string(name: 'IMAGE_NAME', value: params.IMAGE_NAME),
                                string(name: 'IMAGE_TAG', value: params.IMAGE_TAG),
                                string(name: 'CONTAINER_NAME', value: params.CONTAINER_NAME),
                                string(name: 'HOST_PORT', value: params.HOST_PORT),
                                string(name: 'ARTIFACTS_DIR', value: params.ARTIFACTS_DIR),
                                string(name: 'DOCKER_NETWORK', value: params.DOCKER_NETWORK),
                                booleanParam(name: 'RUN_TESTS', value: params.RUN_TESTS),
                                booleanParam(name: 'RECREATE_CONTAINER', value: params.RECREATE_CONTAINER)
                        ]
            }
        }
    }

    post {
        success {
            echo 'Environment setup completed.'
        }
    }
}
