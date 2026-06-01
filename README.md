# JMX to k6 Converter

Spring Boot API for converting JMeter `.jmx` files into k6 JavaScript scripts.

## Requirements

- Java 17 or newer
- Maven 3.8 or newer

## Run

```powershell
mvn spring-boot:run
```

The service starts on:

```text
http://localhost:8080
```

## API

### Convert JMX to k6

Send a multipart `POST` request with the file field named `file`:

```powershell
curl.exe -F "file=@D:\path\to\test.jmx" http://localhost:8080/api/v1/conversions
```

If the JMX references CSV data sets or other local files, upload them as repeated `resources` parts. The generated k6 script uses the original base filename, so the uploaded resource name must match the filename referenced by JMeter, for example `opencart_test_users.csv`.

```powershell
curl.exe -F "file=@D:\path\to\test.jmx" -F "resources=@D:\path\to\opencart_test_users.csv" http://localhost:8080/api/v1/conversions
```

The upload limit is `10MB`.

Example response:

```json
{
  "conversionId": "0e9cd925-a33f-4b89-8cc8-a5e94c111008",
  "sourceFileName": "test.jmx",
  "generatedFileName": "test.k6.js",
  "downloadUrl": "/api/v1/conversions/0e9cd925-a33f-4b89-8cc8-a5e94c111008/script",
  "bundleUrl": "/api/v1/conversions/0e9cd925-a33f-4b89-8cc8-a5e94c111008/bundle",
  "resourceCount": 1,
  "requestCount": 3
}
```

### Download generated script

Use `GET`, not `POST`:

```powershell
curl.exe -o result.k6.js http://localhost:8080/api/v1/conversions/0e9cd925-a33f-4b89-8cc8-a5e94c111008/script
```

### Download k6 bundle

Use this when the script needs CSV or other support files:

```powershell
curl.exe -o result-bundle.zip http://localhost:8080/api/v1/conversions/0e9cd925-a33f-4b89-8cc8-a5e94c111008/bundle
```

## Swagger

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

The OpenAPI JSON document is available at:

```text
http://localhost:8080/v3/api-docs
```

## Health

Spring Actuator health endpoint is available at:

```text
http://localhost:8080/actuator/health
```

Example response:

```json
{
  "status": "UP"
}
```

This endpoint can be used by Docker, CI/CD pipelines, Kubernetes probes, and monitoring systems.

## Build And Test

```powershell
mvn test
mvn package
```

Run the packaged application:

```powershell
java -jar target\jmxtok6changer-1.0-SNAPSHOT.jar
```

## Docker

Build the image:

```powershell
docker build -t jmxtok6changer:local .
```

Run the container:

```powershell
docker run --rm -p 8080:8080 -v ${PWD}\artifacts:/app/artifacts jmxtok6changer:local
```

Check the container health endpoint:

```powershell
curl.exe http://localhost:8080/actuator/health
```

## Jenkins

Jenkins pipeline definitions are available in:

```text
jenkins/
```

There are three jobs:

- `jenkins/configure-server.Jenkinsfile` - runs Ansible to prepare the target Docker host
- `jenkins/setup-environment.Jenkinsfile` - orchestrates Terraform validation, Ansible server setup, and service deploy
- `jenkins/deploy.Jenkinsfile` - builds and deploys the service as a Docker container
- `jenkins/run-k6.Jenkinsfile` - accepts a `CONVERSION_ID`, downloads the generated k6 script, and runs it with Docker
- `jenkins/destroy.Jenkinsfile` - stops and removes the service container and optional resources

See `jenkins/README.md` for parameters and setup notes.

## Ansible

Ansible server configuration is available in:

```text
infra/ansible/
```

It prepares the Docker host used by Jenkins jobs: required packages, Docker service, artifact directory, Docker network, and Docker permissions for the service user.

See `infra/ansible/README.md` for usage.

## Bootstrap

Local bootstrap scripts are available in:

```text
scripts/
```

For a fresh server, copy `scripts/bootstrap-server.env.example` to `scripts/bootstrap-server.env`, fill in the server IP, SSH user, repository URL, and Jenkins password, then run:

```powershell
.\scripts\run-bootstrap.ps1
```

The script connects to the server over SSH, installs prerequisites, clones the project, applies the Terraform Jenkins stack, and waits for Jenkins to become available.

If you prefer to copy the files to the server and run everything there, use:

```bash
cp scripts/server-bootstrap.env.example scripts/server-bootstrap.env
nano scripts/server-bootstrap.env
bash scripts/server-bootstrap.sh
```

See `scripts/README.md` for details.

## Terraform

A Terraform module for provisioning a self-hosted Jenkins CI/CD stack is available in:

```text
infra/terraform/jenkins/
```

It creates a Docker-based Jenkins instance with Configuration as Code and preconfigured jobs for:

- service deploy
- k6 execution by `CONVERSION_ID`
- service destroy

See `infra/terraform/jenkins/README.md` for setup instructions.

## Error Responses

Errors are returned as JSON:

```json
{
  "timestamp": "2026-05-09T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Generated k6 script was not found"
}
```

Typical statuses:

- `400 Bad Request` - missing file, empty upload, or invalid JMX
- `400 Bad Request` - invalid conversion id when downloading a generated script
- `404 Not Found` - unknown URL or missing generated script
- `405 Method Not Allowed` - wrong HTTP method
- `415 Unsupported Media Type` - request is not multipart form data
- `500 Internal Server Error` - storage or unexpected server error
