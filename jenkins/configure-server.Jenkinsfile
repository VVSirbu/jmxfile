pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'ANSIBLE_INVENTORY', defaultValue: 'infra/ansible/inventory/server.example.ini', description: 'Inventory file used by ansible-playbook')
        string(name: 'ANSIBLE_PLAYBOOK', defaultValue: 'infra/ansible/playbooks/configure-server.yml', description: 'Server configuration playbook')
        string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins SSH private key credentials id for the target server')
        string(name: 'ARTIFACTS_DIR', defaultValue: '/opt/jmxtok6changer/artifacts', description: 'Host directory for generated k6 artifacts')
        string(name: 'DOCKER_NETWORK', defaultValue: 'jmxtok6', description: 'Docker network for service and k6 containers')
        string(name: 'SERVICE_USER', defaultValue: 'ubuntu', description: 'Linux user allowed to operate Docker on the target server')
        booleanParam(name: 'CHECK_MODE', defaultValue: false, description: 'Run Ansible in check mode without changing the server')
    }

    environment {
        ANSIBLE_INVENTORY_VALUE = "${params.ANSIBLE_INVENTORY ?: 'infra/ansible/inventory/server.example.ini'}"
        ANSIBLE_PLAYBOOK_VALUE = "${params.ANSIBLE_PLAYBOOK ?: 'infra/ansible/playbooks/configure-server.yml'}"
        ARTIFACTS_DIR_VALUE = "${params.ARTIFACTS_DIR ?: '/opt/jmxtok6changer/artifacts'}"
        DOCKER_NETWORK_VALUE = "${params.DOCKER_NETWORK ?: 'jmxtok6'}"
        SERVICE_USER_VALUE = "${params.SERVICE_USER ?: 'ubuntu'}"
        CHECK_MODE_VALUE = "${params.CHECK_MODE == null ? false : params.CHECK_MODE}"
    }

    stages {
        stage('Validate Parameters') {
            steps {
                sh '''
                    set -eu
                    test -n "$ANSIBLE_INVENTORY_VALUE"
                    test -n "$ANSIBLE_PLAYBOOK_VALUE"
                    test -n "$ARTIFACTS_DIR_VALUE"
                    test -n "$DOCKER_NETWORK_VALUE"
                    test -n "$SERVICE_USER_VALUE"
                    test -f "$ANSIBLE_INVENTORY_VALUE"
                    test -f "$ANSIBLE_PLAYBOOK_VALUE"
                '''
            }
        }

        stage('Run Ansible') {
            steps {
                script {
                    def runAnsible = {
                        sh '''
                            set -eu
                            CHECK_ARGS=""
                            if [ "$CHECK_MODE_VALUE" = "true" ]; then
                              CHECK_ARGS="--check --diff"
                            fi

                            ansible-playbook \
                              -i "$ANSIBLE_INVENTORY_VALUE" \
                              "$ANSIBLE_PLAYBOOK_VALUE" \
                              $CHECK_ARGS \
                              -e "artifacts_dir=$ARTIFACTS_DIR_VALUE" \
                              -e "docker_network=$DOCKER_NETWORK_VALUE" \
                              -e "service_user=$SERVICE_USER_VALUE"
                        '''
                    }

                    if (params.SSH_CREDENTIALS_ID?.trim()) {
                        sshagent(credentials: [params.SSH_CREDENTIALS_ID]) {
                            runAnsible()
                        }
                    } else {
                        runAnsible()
                    }
                }
            }
        }
    }
}
