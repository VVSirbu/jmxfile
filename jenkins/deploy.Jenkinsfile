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
        IMAGE_NAME_VALUE = "${params.IMAGE_NAME ?: 'jmxtok6changer'}"
        IMAGE_TAG_VALUE = "${params.IMAGE_TAG ?: 'latest'}"
        CONTAINER_NAME_VALUE = "${params.CONTAINER_NAME ?: 'jmxtok6changer'}"
        HOST_PORT_VALUE = "${params.HOST_PORT ?: '8080'}"
        ARTIFACTS_DIR_VALUE = "${params.ARTIFACTS_DIR ?: '/opt/jmxtok6changer/artifacts'}"
        DOCKER_NETWORK_VALUE = "${params.DOCKER_NETWORK ?: 'jmxtok6'}"
        RECREATE_CONTAINER_VALUE = "${params.RECREATE_CONTAINER == null ? true : params.RECREATE_CONTAINER}"
        SERVICE_URL = "http://${params.CONTAINER_NAME ?: 'jmxtok6changer'}:8080"
        PUBLIC_SERVICE_URL = "http://localhost:${params.HOST_PORT ?: '8080'}"
    }

    stages {
        stage('Validate Parameters') {
            steps {
                sh '''
                    set -eu
                    test -n "$IMAGE_NAME_VALUE"
                    test -n "$IMAGE_TAG_VALUE"
                    test -n "$CONTAINER_NAME_VALUE"
                    test -n "$HOST_PORT_VALUE"
                    test -n "$ARTIFACTS_DIR_VALUE"
                    test -n "$DOCKER_NETWORK_VALUE"
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
                sh 'docker build -t "$IMAGE_NAME_VALUE:$IMAGE_TAG_VALUE" .'
            }
        }

        stage('Deploy Container') {
            steps {
                sh '''
                    set -eu

                    docker network inspect "$DOCKER_NETWORK_VALUE" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK_VALUE"
                    mkdir -p "$ARTIFACTS_DIR_VALUE"

                    if [ "$RECREATE_CONTAINER_VALUE" = "true" ]; then
                      docker rm -f "$CONTAINER_NAME_VALUE" >/dev/null 2>&1 || true
                    fi

                    if docker ps -a --format '{{.Names}}' | grep -Fx "$CONTAINER_NAME_VALUE" >/dev/null; then
                      echo "Container $CONTAINER_NAME_VALUE already exists. Set RECREATE_CONTAINER=true to replace it."
                      exit 1
                    fi

                    docker run -d \
                      --name "$CONTAINER_NAME_VALUE" \
                      --restart unless-stopped \
                      --network "$DOCKER_NETWORK_VALUE" \
                      -p "$HOST_PORT_VALUE:8080" \
                      -v "$ARTIFACTS_DIR_VALUE:/app/artifacts" \
                      "$IMAGE_NAME_VALUE:$IMAGE_TAG_VALUE"
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    set -eu

                    for attempt in $(seq 1 30); do
                      HEALTH_RESPONSE="$(curl -fsS "$SERVICE_URL/actuator/health" || true)"
                      echo "$HEALTH_RESPONSE"
                      if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
                        echo "Service is UP: $SERVICE_URL"
                        exit 0
                      fi
                      echo "Waiting for service health, attempt $attempt/30"
                      sleep 2
                    done

                    docker logs "$CONTAINER_NAME_VALUE" --tail 200 || true
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            echo "Deployed ${params.CONTAINER_NAME} at ${env.PUBLIC_SERVICE_URL}"
        }
        failure {
            sh 'docker logs "$CONTAINER_NAME_VALUE" --tail 200 || true'
        }
    }
}
