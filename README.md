# Diet Assistant

Single-user personal diet assistant built with Java 21, Spring Boot, Maven, Thymeleaf, Spring MVC, Spring Data JPA, Flyway, Bean Validation, and MySQL.

## Local Startup

Use Java 21 for Maven:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version
```

Start MySQL:

```shell
cp .env.example .env
docker compose up -d mysql
```

Edit `.env` before starting MySQL if you want different local database settings. Docker Compose reads these variables from `.env`:

```text
DIET_ASSISTANT_DB_NAME
DIET_ASSISTANT_DB_USERNAME
DIET_ASSISTANT_DB_PASSWORD
DIET_ASSISTANT_DB_ROOT_PASSWORD
DIET_ASSISTANT_DB_URL
```

Run the application with the local profile:

```shell
set -a
source .env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Open:

```text
http://localhost:8080/
```

The local profile reads the database connection from these environment variables. Load `.env` as shown above or export equivalent values:

```text
DIET_ASSISTANT_DB_URL
DIET_ASSISTANT_DB_USERNAME
DIET_ASSISTANT_DB_PASSWORD
```

Run tests:

```shell
./mvnw test
```
