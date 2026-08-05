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

## Semi-automatic Product Photo Import

The Food Library can create a local pending import from one product's optional front photo and required Nutrition Facts photo. Pending imports are stored under:

```text
data/pending-food-imports/<import-uuid>/
```

The directory is ignored by Git. Photos are not written to the database and a Saved Food is not created during upload or preparation. JPG, JPEG, PNG, HEIC, and HEIF are accepted only when extension, MIME type, and actual content agree.

Each import is first built in a UUID staging directory. Both photos must validate and normalize before the directory is atomically published:

```text
data/pending-food-imports/<import-uuid>/
├── front-original.heic       # only when a front photo was uploaded; extension varies
├── front.jpg                 # fixed Codex input
├── nutrition-original.heic   # extension varies
├── nutrition.jpg             # fixed Codex input
└── metadata.json
```

Originals are retained until confirmation, cancellation, or expiration so an operator can diagnose conversion issues. They are temporary and Codex must only inspect `front.jpg` and `nutrition.jpg`. Metadata contains formats and safe generated filenames only—never browser filenames, profile data, or absolute paths.

After upload and image normalization, the application starts automatic recognition in the background through the replaceable `FoodPhotoRecognizer` interface. The default local implementation runs a controlled `codex exec` process. It attaches only `nutrition.jpg` and optional `front.jpg`, uses a read-only sandbox and a strict JSON output schema, and has a configurable 300-second default timeout. It never gives Codex write access to the pending import directory.

Recognition status is persisted in `metadata.json` as `pending`, `processing`, `ready_for_review`, `recognition_failed`, or `recognition_timed_out`. While recognition is processing, the page polls a read-only status endpoint every 2.5 seconds for up to five minutes and automatically loads the review form when recognition finishes. Manual Refresh Status remains available. Interrupted `pending` or `processing` work is resumed when the application restarts. Failure and timeout preserve the pending photos and leave the editable form available.

The local Codex executable defaults to `codex` on `PATH` and can be configured without storing credentials in the repository:

```shell
export DIET_ASSISTANT_CODEX_EXECUTABLE=/absolute/path/to/codex
export DIET_ASSISTANT_FOOD_PHOTO_RECOGNIZER_ENABLED=true
export DIET_ASSISTANT_CODEX_TIMEOUT_SECONDS=300
```

Authentication is provided by the local Codex installation. No API key is read from repository configuration. To disable automatic recognition while retaining manual photo import, set `DIET_ASSISTANT_FOOD_PHOTO_RECOGNIZER_ENABLED=false`.

The existing local command remains available as a fallback after automatic failure or timeout:

```shell
./scripts/prepare-food-import.sh <import-uuid>
```

Give the printed instruction to Codex. Codex should inspect the photos in that one UUID directory and write `result.json` there. It must use `null` for missing or uncertain values and must not infer zero. Refresh the review page to load either automatic or fallback output into the editable Saved Food form.

The whole pending directory is deleted after a successful confirmation or explicit cancellation. Pending imports expire after 24 hours and are removed during the next pending-import creation or explicit cleanup operation; read-only status checks never perform cleanup. The root directory can be moved outside the repository with `DIET_ASSISTANT_PENDING_FOOD_IMPORT_DIRECTORY`.

### ImageMagick 7 runtime

Photo import requires ImageMagick 7. ImageMagick absence never prevents application startup or manual Saved Food creation. If it is unavailable, the Import Product page explains that photo import is disabled. If ImageMagick 7 works but has no HEIC reader, JPG/PNG remain available and HEIC/HEIF uploads show a libheif capability error.

On macOS install and verify the local runtime:

```shell
brew install imagemagick libheif
magick -version
magick identify -list format | grep -E 'HEIC|HEIF'
./scripts/verify-image-runtime.sh
```

Override the executable when necessary:

```shell
export DIET_ASSISTANT_IMAGEMAGICK_EXECUTABLE=/opt/homebrew/bin/magick
```

Production must supply ImageMagick 7 with a working libheif HEIC reader and the resource policy in `docker/image-runtime/policy.xml`. The reproducible test/runtime image uses digest-pinned Ubuntu 24.04, source-built ImageMagick 7.1.2-29 and libheif 1.23.1, and verifies both source archives with SHA-256 before building:

```shell
docker build --tag diet-assistant-image-runtime:test --file docker/image-runtime/Dockerfile .
docker run --rm diet-assistant-image-runtime:test verify-image-runtime
```

CI builds this Dockerfile with the GitHub Actions build cache, runs the version/capability/synthetic HEIC decode probe, then executes the complete Maven suite in the same image. It never invokes ImageMagick 6 `convert`. A later phase may publish this image to GHCR and pin consumers to its digest; this repository intentionally does not publish it yet.

## Saved Food duplicate review

Manual creation and photo-import confirmation compare the proposed food with active Saved Foods before creating it. Matching uses normalized brand, name, reference amount/unit, and exact nullable nutrition values; notes are intentionally ignored. A server-held, short-lived review token preserves the validated proposal while the user chooses an existing item, creates the new item anyway, or returns to edit it. Inactive foods never block creation and are never reactivated automatically.

The photo path keeps the pending import's atomic confirmation claim, so competing or repeated duplicate-review actions have one terminal result. The first version intentionally has no global database unique constraint: two independent manual requests submitted concurrently can still create identical foods. Avoiding that race without blocking legitimate product variants requires a later, explicit identity design.
