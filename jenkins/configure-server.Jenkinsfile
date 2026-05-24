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

    stages {
        stage('Validate Parameters') {
            steps {
                sh '''
                    set -eu
                    test -n "$ANSIBLE_INVENTORY"
                    test -n "$ANSIBLE_PLAYBOOK"
                    test -n "$ARTIFACTS_DIR"
                    test -n "$DOCKER_NETWORK"
                    test -n "$SERVICE_USER"
                    test -f "$ANSIBLE_INVENTORY"
                    test -f "$ANSIBLE_PLAYBOOK"
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
                            if [ "$CHECK_MODE" = "true" ]; then
                              CHECK_ARGS="--check --diff"
                            fi

                            ansible-playbook \
                              -i "$ANSIBLE_INVENTORY" \
                              "$ANSIBLE_PLAYBOOK" \
                              $CHECK_ARGS \
                              -e "artifacts_dir=$ARTIFACTS_DIR" \
                              -e "docker_network=$DOCKER_NETWORK" \
                              -e "service_user=$SERVICE_USER"
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
