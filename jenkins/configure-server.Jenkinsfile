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
        choice(name: 'SSH_AUTH_MODE', choices: ['key', 'password', 'none'], description: 'How Ansible authenticates to the target server')
        string(name: 'SSH_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins SSH private key credentials id when SSH_AUTH_MODE=key')
        string(name: 'SSH_PASSWORD_CREDENTIALS_ID', defaultValue: '', description: 'Jenkins username/password credentials id when SSH_AUTH_MODE=password')
        string(name: 'ARTIFACTS_DIR', defaultValue: '/opt/jmxtok6changer/artifacts', description: 'Host directory for generated k6 artifacts')
        string(name: 'DOCKER_NETWORK', defaultValue: 'jmxtok6', description: 'Docker network for service and k6 containers')
        string(name: 'SERVICE_USER', defaultValue: 'ubuntu', description: 'Linux user allowed to operate Docker on the target server')
        booleanParam(name: 'CHECK_MODE', defaultValue: false, description: 'Run Ansible in check mode without changing the server')
    }

    environment {
        ANSIBLE_CONFIG = "infra/ansible/ansible.cfg"
        ANSIBLE_HOST_KEY_CHECKING = "False"
        ANSIBLE_INVENTORY_VALUE = "${params.ANSIBLE_INVENTORY ?: ''}"
        TARGET_HOST_VALUE = "${params.TARGET_HOST ?: ''}"
        TARGET_USER_VALUE = "${params.TARGET_USER ?: 'jmxtok6'}"
        SSH_AUTH_MODE_VALUE = "${params.SSH_AUTH_MODE ?: 'key'}"
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

                    case "$SSH_AUTH_MODE_VALUE" in
                      key|password|none) ;;
                      *) echo "Unsupported SSH_AUTH_MODE: $SSH_AUTH_MODE_VALUE"; exit 1 ;;
                    esac
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
                            INVENTORY_USER="${ANSIBLE_LOGIN_USER:-${TARGET_USER_VALUE:-}}"
                            if [ -z "$INVENTORY_FILE" ]; then
                              INVENTORY_FILE=".generated-ansible-inventory.ini"
                              cat > "$INVENTORY_FILE" <<EOF
[jmxtok6]
target ansible_host=${TARGET_HOST_VALUE:-} ansible_user=$INVENTORY_USER
EOF
                            fi

                            CHECK_ARGS=""
                            if [ "$CHECK_MODE_VALUE" = "true" ]; then
                              CHECK_ARGS="--check --diff"
                            fi

                            AUTH_ARGS=""
                            if [ -n "${ANSIBLE_SSH_PASSWORD:-}" ]; then
                              AUTH_ARGS="-e ansible_password=$ANSIBLE_SSH_PASSWORD -e ansible_become_password=$ANSIBLE_SSH_PASSWORD"
                            fi

                            ansible-playbook \
                              -i "$INVENTORY_FILE" \
                              "$ANSIBLE_PLAYBOOK_VALUE" \
                              $CHECK_ARGS \
                              $AUTH_ARGS \
                              -e "artifacts_dir=$ARTIFACTS_DIR_VALUE" \
                              -e "docker_network=$DOCKER_NETWORK_VALUE" \
                              -e "service_user=$SERVICE_USER_VALUE"
                        '''
                    }

                    if (params.SSH_AUTH_MODE == 'password') {
                        withCredentials([usernamePassword(
                                credentialsId: params.SSH_PASSWORD_CREDENTIALS_ID,
                                usernameVariable: 'ANSIBLE_LOGIN_USER',
                                passwordVariable: 'ANSIBLE_SSH_PASSWORD'
                        )]) {
                            runAnsible()
                        }
                    } else if (params.SSH_AUTH_MODE == 'key' && params.SSH_CREDENTIALS_ID?.trim()) {
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
