# Diet Assistant

Single-user personal diet assistant built with Java 21, Spring Boot, Maven, Thymeleaf, Spring MVC, Spring Data JPA, Flyway, Bean Validation, and MySQL 8.4.

## Local Development

### Prerequisites

- Java 21
- Docker Desktop
- Maven wrapper from this repository (`./mvnw`)

Use Java 21 for local Maven commands:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

### Environment File

Docker Compose requires a project-root `.env` file. The real `.env` file is ignored by Git and must be recreated on each new machine:

```shell
cp .env.example .env
```

Edit `.env` before starting MySQL and replace placeholder values with local-only credentials. Never commit `.env`.

Required variables:

```text
DIET_ASSISTANT_DB_NAME
DIET_ASSISTANT_DB_USERNAME
DIET_ASSISTANT_DB_PASSWORD
DIET_ASSISTANT_DB_ROOT_PASSWORD
DIET_ASSISTANT_DB_URL
```

MySQL is exposed locally on `localhost:3307`. The local JDBC URL should be:

```text
jdbc:mysql://localhost:3307/diet_assistant
```

Spring Boot reads these values from `src/main/resources/application-local.yml`, so the application must run with the `local` Spring profile active.

### Start MySQL

Start MySQL with Docker Compose:

```shell
./scripts/start-local-db.sh
```

Or run the commands directly:

```shell
docker compose up -d
docker compose ps
```

Verify the MySQL container is healthy:

```shell
docker compose ps
```

Expected status:

```text
diet-assistant-mysql ... Up ... (healthy) ... 0.0.0.0:3307->3306/tcp
```

### Run Spring Boot

Start the application with the `local` profile:

```shell
./scripts/run-local.sh
```

On macOS, the script uses `/usr/libexec/java_home -v 21` to select Java 21 when available.

Or run the commands directly:

```shell
set -a
source .env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open the application:

```text
http://localhost:8080/
```

### IntelliJ Run Configuration

Configure IntelliJ to use the `local` Spring profile:

1. Open **Run | Edit Configurations**.
2. Select the Spring Boot run configuration for `DietAssistantApplication`.
3. Set **Active profiles** to `local`.
4. Add the required environment variables from `.env`, or use an IntelliJ environment-file plugin if available.
5. Confirm Java 21 is selected for the project SDK and run configuration.

Without the `local` profile, Spring Boot does not load `application-local.yml` and no datasource URL is configured.

## Troubleshooting

### Docker Compose Variables Are Empty

If this command shows empty MySQL values:

```shell
docker compose config | grep MYSQL
```

Then `.env` is missing, named incorrectly, or not in the project root. Recreate it:

```shell
cp .env.example .env
```

Then edit `.env` and replace placeholder values.

### MySQL Exits Because No Root Password Was Provided

This log means Docker Compose started MySQL without `DIET_ASSISTANT_DB_ROOT_PASSWORD`:

```text
Database is uninitialized and password option is not specified
```

Confirm `.env` exists and contains a non-empty `DIET_ASSISTANT_DB_ROOT_PASSWORD`.

### Check Stopped Containers

Stopped containers are not shown by the default `docker compose ps` output. Use:

```shell
docker compose ps -a
```

### Check MySQL Logs

Use:

```shell
docker compose logs mysql
```

### Spring Boot Has No Datasource URL

If Spring Boot fails with:

```text
Failed to configure a DataSource: 'url' attribute is not specified
```

Then the application probably did not start with the `local` profile, or the required environment variables were not loaded.

Start with:

```shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Spring Boot Shows No Active Profile

If the logs show:

```text
No active profile set
```

The `local` profile is not active. Use `./scripts/run-local.sh`, pass `-Dspring-boot.run.profiles=local`, or configure IntelliJ Active profiles as `local`.

### Resetting Docker MySQL Data

This command deletes local database data stored in the Docker volume:

```shell
docker compose down -v
```

Use it only when you intentionally want to reset the local MySQL database.

## Tests

```shell
./mvnw test
```
