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

        string(name: 'ANSIBLE_INVENTORY', defaultValue: '', description: 'Optional inventory file from Git. Leave empty to generate inventory from TARGET_HOST/TARGET_USER')
        string(name: 'TARGET_HOST', defaultValue: '', description: 'Target server IP or hostname for generated Ansible inventory')
        string(name: 'TARGET_USER', defaultValue: 'jmxtok6', description: 'SSH user for generated Ansible inventory')
        choice(name: 'SSH_AUTH_MODE', choices: ['key', 'password', 'none'], description: 'How Ansible authenticates to the target server')
        string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins SSH private key credentials id when SSH_AUTH_MODE=key')
        string(name: 'SSH_PASSWORD_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins username/password credentials id when SSH_AUTH_MODE=password')
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

    environment {
        ANSIBLE_INVENTORY_VALUE = "${params.ANSIBLE_INVENTORY ?: ''}"
        TARGET_HOST_VALUE = "${params.TARGET_HOST ?: ''}"
        TARGET_USER_VALUE = "${params.TARGET_USER ?: 'jmxtok6'}"
        SSH_AUTH_MODE_VALUE = "${params.SSH_AUTH_MODE ?: 'key'}"
        SSH_CREDENTIALS_ID_VALUE = "${params.SSH_CREDENTIALS_ID ?: ''}"
        SSH_PASSWORD_CREDENTIALS_ID_VALUE = "${params.SSH_PASSWORD_CREDENTIALS_ID ?: ''}"
        SERVICE_USER_VALUE = "${params.SERVICE_USER ?: 'ubuntu'}"
        IMAGE_NAME_VALUE = "${params.IMAGE_NAME ?: 'jmxtok6changer'}"
        IMAGE_TAG_VALUE = "${params.IMAGE_TAG ?: 'latest'}"
        CONTAINER_NAME_VALUE = "${params.CONTAINER_NAME ?: 'jmxtok6changer'}"
        HOST_PORT_VALUE = "${params.HOST_PORT ?: '8080'}"
        ARTIFACTS_DIR_VALUE = "${params.ARTIFACTS_DIR ?: '/opt/jmxtok6changer/artifacts'}"
        DOCKER_NETWORK_VALUE = "${params.DOCKER_NETWORK ?: 'jmxtok6'}"
    }

    stages {
        stage('Validate Inputs') {
            steps {
                sh '''
                    set -eu
                    test -n "$ARTIFACTS_DIR_VALUE"
                    test -n "$DOCKER_NETWORK_VALUE"
                    test -n "$IMAGE_NAME_VALUE"
                    test -n "$IMAGE_TAG_VALUE"
                    test -n "$CONTAINER_NAME_VALUE"
                    test -n "$HOST_PORT_VALUE"
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
                                string(name: 'ANSIBLE_INVENTORY', value: env.ANSIBLE_INVENTORY_VALUE),
                                string(name: 'TARGET_HOST', value: env.TARGET_HOST_VALUE),
                                string(name: 'TARGET_USER', value: env.TARGET_USER_VALUE),
                                string(name: 'ANSIBLE_PLAYBOOK', value: 'infra/ansible/playbooks/configure-server.yml'),
                                string(name: 'SSH_AUTH_MODE', value: env.SSH_AUTH_MODE_VALUE),
                                string(name: 'SSH_CREDENTIALS_ID', value: env.SSH_CREDENTIALS_ID_VALUE),
                                string(name: 'SSH_PASSWORD_CREDENTIALS_ID', value: env.SSH_PASSWORD_CREDENTIALS_ID_VALUE),
                                string(name: 'ARTIFACTS_DIR', value: env.ARTIFACTS_DIR_VALUE),
                                string(name: 'DOCKER_NETWORK', value: env.DOCKER_NETWORK_VALUE),
                                string(name: 'SERVICE_USER', value: env.SERVICE_USER_VALUE),
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
                                string(name: 'IMAGE_NAME', value: env.IMAGE_NAME_VALUE),
                                string(name: 'IMAGE_TAG', value: env.IMAGE_TAG_VALUE),
                                string(name: 'CONTAINER_NAME', value: env.CONTAINER_NAME_VALUE),
                                string(name: 'HOST_PORT', value: env.HOST_PORT_VALUE),
                                string(name: 'ARTIFACTS_DIR', value: env.ARTIFACTS_DIR_VALUE),
                                string(name: 'DOCKER_NETWORK', value: env.DOCKER_NETWORK_VALUE),
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
