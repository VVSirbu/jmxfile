pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
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
        SERVICE_URL = "http://localhost:${params.HOST_PORT}"
    }

    stages {
        stage('Validate Parameters') {
            steps {
                sh '''
                    set -eu
                    test -n "$IMAGE_NAME"
                    test -n "$IMAGE_TAG"
                    test -n "$CONTAINER_NAME"
                    test -n "$HOST_PORT"
                    test -n "$ARTIFACTS_DIR"
                    test -n "$DOCKER_NETWORK"
                '''
            }
        }

        stage('Test') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                sh 'mvn -B test'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t "$IMAGE_NAME:$IMAGE_TAG" .'
            }
        }

        stage('Deploy Container') {
            steps {
                sh '''
                    set -eu

                    docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"
                    mkdir -p "$ARTIFACTS_DIR"

                    if [ "$RECREATE_CONTAINER" = "true" ]; then
                      docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
                    fi

                    if docker ps -a --format '{{.Names}}' | grep -Fx "$CONTAINER_NAME" >/dev/null; then
                      echo "Container $CONTAINER_NAME already exists. Set RECREATE_CONTAINER=true to replace it."
                      exit 1
                    fi

                    docker run -d \
                      --name "$CONTAINER_NAME" \
                      --restart unless-stopped \
                      --network "$DOCKER_NETWORK" \
                      -p "$HOST_PORT:8080" \
                      -v "$ARTIFACTS_DIR:/app/artifacts" \
                      "$IMAGE_NAME:$IMAGE_TAG"
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    set -eu

                    for attempt in $(seq 1 30); do
                      if curl -fsS "$SERVICE_URL/actuator/health" | grep -q '"status":"UP"'; then
                        echo "Service is UP: $SERVICE_URL"
                        exit 0
                      fi
                      echo "Waiting for service health, attempt $attempt/30"
                      sleep 2
                    done

                    docker logs "$CONTAINER_NAME" --tail 200 || true
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            echo "Deployed ${params.CONTAINER_NAME} at ${env.SERVICE_URL}"
        }
        failure {
            sh 'docker logs "$CONTAINER_NAME" --tail 200 || true'
        }
    }
}
