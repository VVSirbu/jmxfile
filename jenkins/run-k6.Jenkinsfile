pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'CONVERSION_ID', defaultValue: '', description: 'Conversion/job id returned by the service')
        string(name: 'SERVICE_URL', defaultValue: 'http://localhost:8080', description: 'Base URL of the JMX to k6 service')
        string(name: 'K6_IMAGE', defaultValue: 'grafana/k6:latest', description: 'Docker image used to run k6')
        string(name: 'K6_ARGS', defaultValue: '--summary-export summary.json', description: 'Extra arguments passed to k6 run')
        string(name: 'RESULTS_DIR', defaultValue: 'k6-results', description: 'Workspace directory for downloaded script and reports')
        string(name: 'DOCKER_NETWORK', defaultValue: 'jmxtok6', description: 'Docker network used by the k6 container')
        booleanParam(name: 'CLEAN_RESULTS', defaultValue: true, description: 'Clean previous k6 result files before running')
    }

    stages {
        stage('Validate Parameters') {
            steps {
                sh '''
                    set -eu
                    test -n "$CONVERSION_ID"
                    test -n "$SERVICE_URL"
                    test -n "$K6_IMAGE"
                    test -n "$RESULTS_DIR"
                    test -n "$DOCKER_NETWORK"
                '''
            }
        }

        stage('Prepare Results Directory') {
            steps {
                sh '''
                    set -eu
                    if [ "$CLEAN_RESULTS" = "true" ]; then
                      rm -rf "$RESULTS_DIR"
                    fi
                    mkdir -p "$RESULTS_DIR"
                '''
            }
        }

        stage('Download k6 Script') {
            steps {
                sh '''
                    set -eu
                    SCRIPT_URL="${SERVICE_URL%/}/api/v1/conversions/$CONVERSION_ID/script"
                    echo "Downloading generated script from $SCRIPT_URL"
                    curl -fsS "$SCRIPT_URL" -o "$RESULTS_DIR/$CONVERSION_ID.k6.js"
                    test -s "$RESULTS_DIR/$CONVERSION_ID.k6.js"
                    cat > "$RESULTS_DIR/run-metadata.json" <<EOF
{
  "conversionId": "$CONVERSION_ID",
  "serviceUrl": "$SERVICE_URL",
  "k6Image": "$K6_IMAGE",
  "k6Args": "$K6_ARGS",
  "buildUrl": "$BUILD_URL"
}
EOF
                '''
            }
        }

        stage('Run k6') {
            steps {
                sh '''
                    set -eu
                    docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"
                    set +e
                    docker run --rm \
                      --network "$DOCKER_NETWORK" \
                      -v "$PWD/$RESULTS_DIR:/scripts" \
                      -w /scripts \
                      "$K6_IMAGE" run $K6_ARGS "$CONVERSION_ID.k6.js" > "$RESULTS_DIR/k6-output.log" 2>&1
                    K6_EXIT_CODE=$?
                    set -e
                    cat "$RESULTS_DIR/k6-output.log"
                    exit "$K6_EXIT_CODE"
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: "${params.RESULTS_DIR}/**", allowEmptyArchive: true
        }
    }
}
