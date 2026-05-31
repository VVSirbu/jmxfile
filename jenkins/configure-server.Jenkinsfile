pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'ANSIBLE_INVENTORY', defaultValue: '', description: 'Optional inventory file from Git. Leave empty to generate inventory from TARGET_HOST/TARGET_USER')
        string(name: 'TARGET_HOST', defaultValue: '', description: 'Target server IP or hostname used when ANSIBLE_INVENTORY is empty')
        string(name: 'TARGET_USER', defaultValue: 'jmxtok6', description: 'SSH user for generated Ansible inventory')
        string(name: 'ANSIBLE_PLAYBOOK', defaultValue: 'infra/ansible/playbooks/configure-server.yml', description: 'Server configuration playbook')
        string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins SSH private key credentials id for the target server')
        string(name: 'ARTIFACTS_DIR', defaultValue: '/opt/jmxtok6changer/artifacts', description: 'Host directory for generated k6 artifacts')
        string(name: 'DOCKER_NETWORK', defaultValue: 'jmxtok6', description: 'Docker network for service and k6 containers')
        string(name: 'SERVICE_USER', defaultValue: 'ubuntu', description: 'Linux user allowed to operate Docker on the target server')
        booleanParam(name: 'CHECK_MODE', defaultValue: false, description: 'Run Ansible in check mode without changing the server')
    }

    environment {
        ANSIBLE_INVENTORY_VALUE = "${params.ANSIBLE_INVENTORY ?: ''}"
        TARGET_HOST_VALUE = "${params.TARGET_HOST ?: ''}"
        TARGET_USER_VALUE = "${params.TARGET_USER ?: 'jmxtok6'}"
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
                    test -n "$ANSIBLE_PLAYBOOK_VALUE"
                    test -n "$ARTIFACTS_DIR_VALUE"
                    test -n "$DOCKER_NETWORK_VALUE"
                    test -n "$SERVICE_USER_VALUE"
                    test -f "$ANSIBLE_PLAYBOOK_VALUE"

                    if [ -n "${ANSIBLE_INVENTORY_VALUE:-}" ]; then
                      test -f "${ANSIBLE_INVENTORY_VALUE}"
                    else
                      test -n "${TARGET_HOST_VALUE:-}"
                      test -n "${TARGET_USER_VALUE:-}"
                    fi
                '''
            }
        }

        stage('Run Ansible') {
            steps {
                script {
                    def runAnsible = {
                        sh '''
                            set -eu
                            INVENTORY_FILE="${ANSIBLE_INVENTORY_VALUE:-}"
                            if [ -z "$INVENTORY_FILE" ]; then
                              INVENTORY_FILE=".generated-ansible-inventory.ini"
                              cat > "$INVENTORY_FILE" <<EOF
[jmxtok6]
target ansible_host=${TARGET_HOST_VALUE:-} ansible_user=${TARGET_USER_VALUE:-}
EOF
                            fi

                            CHECK_ARGS=""
                            if [ "$CHECK_MODE_VALUE" = "true" ]; then
                              CHECK_ARGS="--check --diff"
                            fi

                            ansible-playbook \
                              -i "$INVENTORY_FILE" \
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
