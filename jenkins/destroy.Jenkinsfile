pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'CONTAINER_NAME', defaultValue: 'jmxtok6changer', description: 'Docker container name to stop and remove')
        string(name: 'IMAGE_NAME', defaultValue: 'jmxtok6changer', description: 'Docker image name')
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image tag')
        string(name: 'ARTIFACTS_DIR', defaultValue: '/opt/jmxtok6changer/artifacts', description: 'Host artifacts directory')
        string(name: 'DOCKER_NETWORK', defaultValue: 'jmxtok6', description: 'Docker network name')
        booleanParam(name: 'REMOVE_IMAGE', defaultValue: false, description: 'Remove Docker image after deleting the container')
        booleanParam(name: 'REMOVE_ARTIFACTS', defaultValue: false, description: 'Remove generated k6 artifacts from the host')
        booleanParam(name: 'REMOVE_NETWORK', defaultValue: false, description: 'Remove Docker network')
        string(name: 'CONFIRM_DESTROY', defaultValue: '', description: 'Type DELETE to confirm destructive actions')
    }

    stages {
        stage('Validate Confirmation') {
            steps {
                sh '''
                    set -eu
                    if [ "$CONFIRM_DESTROY" != "DELETE" ]; then
                      echo "Refusing to destroy resources. Set CONFIRM_DESTROY=DELETE."
                      exit 1
                    fi
                    test -n "$CONTAINER_NAME"
                    test -n "$IMAGE_NAME"
                    test -n "$IMAGE_TAG"
                    test -n "$ARTIFACTS_DIR"
                    test -n "$DOCKER_NETWORK"
                '''
            }
        }

        stage('Stop And Remove Container') {
            steps {
                sh '''
                    set -eu
                    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
                    echo "Container removed: $CONTAINER_NAME"
                '''
            }
        }

        stage('Remove Image') {
            when {
                expression { return params.REMOVE_IMAGE }
            }
            steps {
                sh '''
                    set -eu
                    docker rmi "$IMAGE_NAME:$IMAGE_TAG" >/dev/null 2>&1 || true
                    echo "Image removed if it existed: $IMAGE_NAME:$IMAGE_TAG"
                '''
            }
        }

        stage('Remove Artifacts') {
            when {
                expression { return params.REMOVE_ARTIFACTS }
            }
            steps {
                sh '''
                    set -eu
                    case "$ARTIFACTS_DIR" in
                      ""|"/"|"/opt"|"/opt/"|"."|"..")
                        echo "Refusing unsafe ARTIFACTS_DIR: $ARTIFACTS_DIR"
                        exit 1
                        ;;
                    esac
                    rm -rf "$ARTIFACTS_DIR"
                    echo "Artifacts removed: $ARTIFACTS_DIR"
                '''
            }
        }

        stage('Remove Network') {
            when {
                expression { return params.REMOVE_NETWORK }
            }
            steps {
                sh '''
                    set -eu
                    docker network rm "$DOCKER_NETWORK" >/dev/null 2>&1 || true
                    echo "Network removed if it existed: $DOCKER_NETWORK"
                '''
            }
        }
    }
}
