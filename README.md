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

The upload limit is `10MB`.

Example response:

```json
{
  "conversionId": "0e9cd925-a33f-4b89-8cc8-a5e94c111008",
  "sourceFileName": "test.jmx",
  "generatedFileName": "test.k6.js",
  "downloadUrl": "/api/v1/conversions/0e9cd925-a33f-4b89-8cc8-a5e94c111008/script",
  "requestCount": 3
}
```

### Download generated script

Use `GET`, not `POST`:

```powershell
curl.exe -o result.k6.js http://localhost:8080/api/v1/conversions/0e9cd925-a33f-4b89-8cc8-a5e94c111008/script
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
