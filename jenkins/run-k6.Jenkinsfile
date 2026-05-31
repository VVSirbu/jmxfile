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

    environment {
        CONVERSION_ID_VALUE = "${params.CONVERSION_ID ?: ''}"
        SERVICE_URL_VALUE = "${params.SERVICE_URL ?: 'http://localhost:8080'}"
        K6_IMAGE_VALUE = "${params.K6_IMAGE ?: 'grafana/k6:latest'}"
        K6_ARGS_VALUE = "${params.K6_ARGS ?: '--summary-export summary.json'}"
        RESULTS_DIR_VALUE = "${params.RESULTS_DIR ?: 'k6-results'}"
        DOCKER_NETWORK_VALUE = "${params.DOCKER_NETWORK ?: 'jmxtok6'}"
        CLEAN_RESULTS_VALUE = "${params.CLEAN_RESULTS == null ? true : params.CLEAN_RESULTS}"
    }

    stages {
        stage('Validate Parameters') {
            steps {
                sh '''
                    set -eu
                    test -n "$CONVERSION_ID_VALUE"
                    test -n "$SERVICE_URL_VALUE"
                    test -n "$K6_IMAGE_VALUE"
                    test -n "$RESULTS_DIR_VALUE"
                    test -n "$DOCKER_NETWORK_VALUE"
                '''
            }
        }

        stage('Prepare Results Directory') {
            steps {
                sh '''
                    set -eu
                    if [ "$CLEAN_RESULTS_VALUE" = "true" ]; then
                      rm -rf "$RESULTS_DIR_VALUE"
                    fi
                    mkdir -p "$RESULTS_DIR_VALUE"
                '''
            }
        }

        stage('Download k6 Script') {
            steps {
                sh '''
                    set -eu
                    SCRIPT_URL="${SERVICE_URL_VALUE%/}/api/v1/conversions/$CONVERSION_ID_VALUE/script"
                    echo "Downloading generated script from $SCRIPT_URL"
                    curl -fsS "$SCRIPT_URL" -o "$RESULTS_DIR_VALUE/$CONVERSION_ID_VALUE.k6.js"
                    test -s "$RESULTS_DIR_VALUE/$CONVERSION_ID_VALUE.k6.js"
                    cat > "$RESULTS_DIR_VALUE/run-metadata.json" <<EOF
{
  "conversionId": "$CONVERSION_ID_VALUE",
  "serviceUrl": "$SERVICE_URL_VALUE",
  "k6Image": "$K6_IMAGE_VALUE",
  "k6Args": "$K6_ARGS_VALUE",
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
                    docker network inspect "$DOCKER_NETWORK_VALUE" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK_VALUE"
                    set +e
                    docker run --rm \
                      --network "$DOCKER_NETWORK_VALUE" \
                      -v "$PWD/$RESULTS_DIR_VALUE:/scripts" \
                      -w /scripts \
                      "$K6_IMAGE_VALUE" run $K6_ARGS_VALUE "$CONVERSION_ID_VALUE.k6.js" > "$RESULTS_DIR_VALUE/k6-output.log" 2>&1
                    K6_EXIT_CODE=$?
                    set -e
                    cat "$RESULTS_DIR_VALUE/k6-output.log"
                    exit "$K6_EXIT_CODE"
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: "${env.RESULTS_DIR_VALUE}/**", allowEmptyArchive: true
        }
    }
}
