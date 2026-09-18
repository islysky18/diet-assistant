# Diet Assistant

A full-stack, local-first nutrition tracking application that brings food logging, reusable nutrition data, product-photo recognition, USDA FoodData Central, and daily activity tracking into one workflow.

**Java 21** · **Spring Boot 4.1** · **Spring MVC** · **Thymeleaf** · **MySQL 8.4** · **Flyway** · **Playwright** · **Docker**

> This project is currently designed for one user running the application locally. It is an engineering portfolio project and an actively used personal tool—not medical software.

## Why I Built This

Nutrition tracking becomes tedious when every meal requires repeated manual entry, packaged-food labels must be copied by hand, and activity data lives separately from food data.

Diet Assistant reduces that friction with reusable Saved Foods, authoritative USDA data, photo-assisted product entry, recent-food shortcuts, and daily energy aggregation. Every automated import ends in an editable review step, so convenience never removes user control.

## Key Features

- Log meals by date and meal type with calculated calories, protein, carbohydrates, fat, and fiber.
- Reuse a searchable Saved Food Library with serving and weight-unit conversion.
- Quick-log recently eaten foods while preserving the prior entry's amount and nutrition snapshot.
- Import generic foods from USDA FoodData Central using a server-side API integration.
- Create packaged foods from front-label and Nutrition Facts photos with AI-assisted extraction.
- Review and edit imported values before anything is saved.
- Detect exact duplicate Saved Foods and let the user reuse, edit, or intentionally create a separate record.
- Track daily and weekly nutrition against configurable goals.
- Combine manual activity totals with Apple Health daily aggregates sent by an authenticated iOS Shortcut.
- Show calories consumed, energy burned, current deficit or surplus, steps, exercise minutes, and a simple end-of-day estimate.
- Store basic health metrics and profile context locally.

## Main Workflows

### Product photo to reusable food

```text
Front photo + Nutrition Facts photo
                 ↓
       Validate and normalize images
                 ↓
       AI structured extraction
                 ↓
          Editable user review
                 ↓
          Duplicate detection
                 ↓
            Saved Food Library
                 ↓
              Food Entry
```

The recognizer never writes a Saved Food directly. Missing or uncertain nutrition values remain `null`, explicit zero remains zero, and the user confirms the final data.

### Apple Health to daily energy balance

```text
Apple Health
     ↓
iOS Shortcut
     ↓
Authenticated local REST API
     ↓
Daily energy aggregate
     ↓
Today dashboard
```

The sync stores one aggregate per day and is safe to repeat. It does not expose the application to the public internet.

## Screenshots

> Screenshots and a short demo are planned for the next portfolio-polish phase.

## Architecture

Diet Assistant is a feature-oriented modular monolith. HTTP controllers delegate to application services, which own business rules and persistence coordination.

```text
Browser / iOS Shortcut
          ↓
Spring MVC controllers + Thymeleaf views
          ↓
Feature services
  ├── Food Library and Food Entry
  ├── Nutrition goals and summaries
  ├── Daily energy and health metrics
  ├── USDA integration
  └── Product-photo import
          ↓
Spring Data JPA repositories
          ↓
MySQL + Flyway migrations
```

External and local integrations are isolated behind application boundaries:

- USDA FoodData Central supplies authoritative food search and nutrient details.
- `FoodPhotoRecognizer` keeps AI-assisted extraction replaceable; the local implementation invokes the Codex CLI.
- ImageMagick validates, decodes, and normalizes uploaded product images.
- Apple Health data reaches the application through an iOS Shortcut and a Bearer-token-protected local endpoint.

## Engineering Highlights

- **Historical accuracy:** Food Entries store nutrition snapshots, so editing a Saved Food never rewrites past meals.
- **Explicit trust boundary:** AI output is validated as untrusted structured input and always remains editable before persistence.
- **Replaceable recognition:** Product recognition is accessed through `FoodPhotoRecognizer`, keeping business logic independent of one AI runtime.
- **Safe import lifecycle:** Photo uploads use staging, validation, atomic publishing, persisted states, recovery after restart, expiration, and terminal cleanup.
- **Duplicate review:** Matching is separated from persistence and uses short-lived server-held proposals instead of trusting round-tripped form data.
- **Idempotent health sync:** Apple Health aggregates upsert by date and reject stale updates using the source timestamp.
- **Nullable nutrition semantics:** Unknown values remain distinct from explicit zero and contribute zero only at the aggregation boundary.
- **Deterministic schema evolution:** Flyway owns all MySQL schema changes; JPA validates rather than creates the schema.
- **Real-environment testing:** Integration and browser tests run against the real Spring Boot application and isolated MySQL instances.
- **Reproducible image runtime:** CI builds a pinned ImageMagick/libheif container and verifies synthetic HEIC decoding before the Maven suite.

## Tech Stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring MVC, Bean Validation |
| Views | Thymeleaf, HTML, CSS, JavaScript |
| Data | Spring Data JPA, MySQL 8.4, Flyway |
| Integrations | USDA FoodData Central, Codex CLI, ImageMagick, Apple Health, iOS Shortcuts |
| Testing | JUnit 5, Spring Boot Test, Testcontainers, Playwright |
| Infrastructure | Maven, Docker Compose, GitHub Actions |

## Getting Started

### Prerequisites

- Java 21
- Docker Desktop
- Maven wrapper from this repository (`./mvnw`)

For browser tests, also install Node.js 22 or later.

### Quick start

```shell
cp .env.example .env
```

Replace the placeholder passwords in `.env`, then run:

```shell
./scripts/start-local-db.sh
./scripts/run-local.sh
```

Open [http://localhost:8080/](http://localhost:8080/).

The local launcher selects Java 21 on macOS when available, safely loads supported values from `.env` without executing the file as shell code, maps the database values to Spring Boot's datasource properties, and activates the `local` Spring profile. A fresh clone does not require an untracked `application-local.yml`.

### Local configuration

Required database variables:

```text
DIET_ASSISTANT_DB_NAME
DIET_ASSISTANT_DB_USERNAME
DIET_ASSISTANT_DB_PASSWORD
DIET_ASSISTANT_DB_ROOT_PASSWORD
DIET_ASSISTANT_DB_URL
```

MySQL is exposed on `localhost:3307`, so the local JDBC URL is:

```text
jdbc:mysql://localhost:3307/diet_assistant
```

Optional integrations use:

```text
USDA_FDC_API_KEY
DIET_ASSISTANT_LOCAL_SYNC_TOKEN
DIET_ASSISTANT_FOOD_PHOTO_RECOGNIZER_ENABLED
DIET_ASSISTANT_CODEX_EXECUTABLE
DIET_ASSISTANT_CODEX_TIMEOUT_SECONDS
DIET_ASSISTANT_IMAGEMAGICK_EXECUTABLE
DIET_ASSISTANT_PENDING_FOOD_IMPORT_DIRECTORY
```

`USDA_FDC_API_KEY` and `DIET_ASSISTANT_LOCAL_SYNC_TOKEN` may be empty. When absent, their integrations are safely unavailable while browser workflows continue to work.

### IntelliJ

1. Open **Run | Edit Configurations**.
2. Select the Spring Boot configuration for `DietAssistantApplication`.
3. Set **Active profiles** to `local`.
4. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` to the corresponding database values in `.env`.
5. Add optional integration variables directly when needed, including `USDA_FDC_API_KEY` and `DIET_ASSISTANT_LOCAL_SYNC_TOKEN`.
6. Select Java 21 for the project SDK and run configuration.

Using `./scripts/run-local.sh` remains the recommended path because it performs this mapping automatically.

## Optional Integrations

### USDA FoodData Central

Saved Foods can be searched and imported from USDA FoodData Central. Add a key from the FoodData Central service to the uncommitted `.env` file:

```text
USDA_FDC_API_KEY=your-key
```

The key remains on the Spring Boot server. When it is missing, USDA search is shown as unavailable; manual Saved Food creation and product-photo import continue normally. Imported records consistently use a 100 g nutrition basis and enter the same editable duplicate-review flow as manually created foods.

### Semi-automatic product photo import

The Food Library accepts one product's optional front photo and required Nutrition Facts photo. JPG, JPEG, PNG, HEIC, and HEIF are accepted only when extension, MIME type, and decoded content agree.

Pending imports live under:

```text
data/pending-food-imports/<import-uuid>/
```

Each import is built in a private staging directory. All photos must validate and normalize before the directory is atomically published:

```text
data/pending-food-imports/<import-uuid>/
├── front-original.heic       # optional; extension varies
├── front.jpg                 # normalized recognizer input
├── nutrition-original.heic   # extension varies
├── nutrition.jpg             # normalized recognizer input
└── metadata.json
```

Originals are retained until confirmation, cancellation, or expiration for conversion diagnostics. Metadata contains safe generated filenames and formats only—never browser filenames, profile data, or absolute paths.

After normalization, recognition starts in the background through `FoodPhotoRecognizer`. The default local implementation launches a controlled `codex exec` process with:

- only `nutrition.jpg` and optional `front.jpg` attached;
- a read-only sandbox and strict JSON output schema;
- no write access to the pending-import directory; and
- a configurable 300-second default timeout.

Recognition states are `pending`, `processing`, `ready_for_review`, `recognition_failed`, and `recognition_timed_out`. The page polls a read-only endpoint every 2.5 seconds for up to five minutes and loads the review form when recognition finishes. Interrupted work resumes after application restart; failure and timeout preserve the editable manual fallback.

Configure the recognizer without storing credentials in the repository:

```shell
export DIET_ASSISTANT_CODEX_EXECUTABLE=/absolute/path/to/codex
export DIET_ASSISTANT_FOOD_PHOTO_RECOGNIZER_ENABLED=true
export DIET_ASSISTANT_CODEX_TIMEOUT_SECONDS=300
```

Authentication comes from the local Codex installation. To keep manual photo import but disable automatic recognition:

```shell
export DIET_ASSISTANT_FOOD_PHOTO_RECOGNIZER_ENABLED=false
```

After an automatic failure or timeout, the existing local fallback remains available:

```shell
./scripts/prepare-food-import.sh <import-uuid>
```

The printed instruction asks Codex to inspect only that UUID's normalized images and write `result.json`. Unknown values must be `null`, never an inferred zero. Refreshing the review page loads the result into the editable form.

The complete pending directory is removed after successful confirmation or explicit cancellation. Imports expire after 24 hours and are cleaned during the next creation or explicit cleanup operation; read-only status checks never trigger deletion. Change the root with `DIET_ASSISTANT_PENDING_FOOD_IMPORT_DIRECTORY`.

#### ImageMagick 7 runtime

Photo import requires ImageMagick 7, but its absence never prevents application startup or manual Saved Food creation. Without a working HEIC reader, JPG and PNG remain available while HEIC/HEIF uploads show a capability error.

Install and verify the macOS runtime:

```shell
brew install imagemagick libheif
magick -version
magick identify -list format | grep -E 'HEIC|HEIF'
./scripts/verify-image-runtime.sh
```

Override the executable when needed:

```shell
export DIET_ASSISTANT_IMAGEMAGICK_EXECUTABLE=/opt/homebrew/bin/magick
```

The reproducible test/runtime image uses digest-pinned Ubuntu 24.04, source-built ImageMagick 7.1.2-29 and libheif 1.23.1, and SHA-256 verification for both source archives:

```shell
docker build --tag diet-assistant-image-runtime:test --file docker/image-runtime/Dockerfile .
docker run --rm diet-assistant-image-runtime:test verify-image-runtime
```

CI verifies the runtime and synthetic HEIC decoding, then runs the Maven suite in the same image. The repository does not publish this image yet.

### Saved Food duplicate review

Manual creation and photo-import confirmation compare a proposed food with active Saved Foods before persistence. Exact matching uses normalized brand, name, reference amount and unit, reference weight, and nullable nutrition values; notes are ignored.

A short-lived, server-held review token preserves the validated proposal while the user chooses an existing item, creates the new item anyway, or returns to edit. Inactive foods never block creation and are not reactivated automatically.

Photo confirmation retains its atomic claim, so repeated or competing review actions have one terminal outcome. There is intentionally no global database unique constraint yet: independent concurrent manual requests can still create identical foods because preventing that race requires an explicit identity model that does not block legitimate product variants.

### Daily energy and Apple Health sync

The Today dashboard combines Food Entry snapshot calories with activity aggregates. It shows active and resting energy, burned energy so far, steps, exercise minutes, current balance, and—for today only—an end-of-day estimate.

```text
total burned = active energy + resting energy
energy balance = calories consumed - total burned
projected resting energy = resting energy so far × 24 / elapsed hours
projected total burn = active energy so far + projected resting energy
```

A negative energy balance is displayed as a positive `deficit`; a positive balance is a positive `surplus`. Projection begins after one elapsed hour, uses the saved IANA timezone and application clock, never projects active energy, and remains unavailable when resting data is missing. These are tracking estimates, not medical measurements.

Use **Enter activity totals** or **Edit daily activity totals** on Today to maintain manual values. Blank fields remain unknown; entered zero remains zero. When Apple Health and manual data coexist, each non-null Apple Health field wins and the manual field is only its fallback—the sources are never added together.

#### Local Wi-Fi API

Set a long random secret in `.env`. Without it, `/api/health/**` returns HTTP 401 while browser pages remain available.

```text
DIET_ASSISTANT_LOCAL_SYNC_TOKEN=REPLACE_WITH_A_LONG_RANDOM_LOCAL_TOKEN
```

Test the upsert with a date matching the application clock:

```shell
curl -i \
  -X PUT \
  "http://localhost:8080/api/health/daily-energy/2026-08-05" \
  -H "Authorization: Bearer <local-sync-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "activeEnergyKcal": 540.30,
    "restingEnergyKcal": 1260.80,
    "steps": 8421,
    "exerciseMinutes": 47,
    "timezone": "America/Los_Angeles",
    "sourceUpdatedAt": "2026-08-05T20:00:00-07:00"
  }'
```

Repeated requests update the same date. A request older than the saved `sourceUpdatedAt`, or a request without a timestamp attempting to replace timestamped data, receives HTTP 409.

For LAN access, keep the Mac and iPhone on the same Wi-Fi and open `http://<mac-lan-ip>:8080/` in iPhone Safari. The Shortcut endpoint is `http://<mac-lan-ip>:8080/api/health/daily-energy/YYYY-MM-DD`; `localhost` on the iPhone refers to the phone, not the Mac. Router forwarding and public exposure are not required.

#### Build the “Sync Diet Assistant” Shortcut

1. Get the current date and define the local day's start and end.
2. Find Active Energy samples in that range and calculate their sum.
3. Repeat for Resting Energy and Steps.
4. Optionally include Exercise Minutes when the installed iOS version exposes it reliably.
5. Build a dictionary with `activeEnergyKcal`, `restingEnergyKcal`, `steps`, optional `exerciseMinutes`, the iPhone's IANA `timezone`, and an offset-aware `sourceUpdatedAt`.
6. Add **Get Contents of URL**, use PUT with JSON, the LAN endpoint, and `Authorization: Bearer <local-sync-token>` plus `Content-Type: application/json`.
7. Show a success message for a 2xx response and the returned error otherwise.

Grant Shortcuts access to the requested Health data. Filter every metric to the same local-day range and sum each sample set once. The backend upserts one Apple Health row per day, so rerunning the Shortcut is safe.

## Testing

Run the Java test suite:

```shell
./mvnw test
```

The suite includes unit, MVC, and MySQL integration coverage with JUnit, Spring Boot Test, and Testcontainers.

For browser tests:

```shell
npm ci
npx playwright install chromium
npm run test:ui
```

The UI command starts an isolated MySQL container and the real Spring Boot application on port 18080, waits for readiness, runs Chromium tests, and cleans up both processes. It does not use `.env` or existing developer data.

Playwright covers `/foods` and `/food` at desktop `1280 × 800` and mobile `390 × 844`, including important controls, checkbox sizing, responsive layout, and horizontal overflow. Screenshots, traces, and video are retained on failure; pixel-perfect baselines are deliberately avoided to reduce cross-environment font-rendering flakes.

GitHub Actions additionally builds and verifies the pinned image runtime before running the Maven suite.

## Troubleshooting

### Docker Compose variables are empty

If `docker compose config` shows empty MySQL values, `.env` is missing, misnamed, or outside the project root:

```shell
cp .env.example .env
```

Replace every required placeholder before retrying.

### MySQL exits without a root password

If the log says `Database is uninitialized and password option is not specified`, make sure `DIET_ASSISTANT_DB_ROOT_PASSWORD` is present and non-empty in `.env`.

Inspect stopped containers and logs with:

```shell
docker compose ps -a
docker compose logs mysql
```

### Spring Boot has no datasource URL

`Failed to configure a DataSource: 'url' attribute is not specified` means the datasource variables were not mapped. `No active profile set` means the local runtime profile was not selected. Start through the launcher, which handles both:

```shell
./scripts/run-local.sh
```

### Spring Boot uses the wrong Java version

An `UnsupportedClassVersionError` mentioning class-file version `52.0` indicates Java 8. This project requires Java 21:

```shell
java -version
```

On macOS:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

The local launcher performs this selection and validation automatically when possible.

### Reset local database data

The following command permanently deletes the local MySQL Docker volume:

```shell
docker compose down -v
```

Use it only when an intentional clean reset is needed.

## Current Scope and Limitations

- Single-user and local-first; there is no authentication or multi-user isolation.
- No public cloud deployment or public HTTPS endpoint yet.
- Apple Health uses a manually triggered iOS Shortcut, not native HealthKit or background sync.
- The Mac, Spring Boot application, MySQL, and iPhone must be available on the same LAN for health sync.
- Product recognition depends on a local Codex installation and always requires editable human review.
- Duplicate matching is exact rather than fuzzy and does not enforce a global database uniqueness rule.
- Only Apple Health daily aggregates are stored; energy expenditure and projections are estimates.
- Nutrition and health information is for personal tracking and is not medical advice.

## Roadmap

- Add screenshots and a short demo for portfolio presentation.
- Improve the mobile browser experience and evaluate a PWA workflow.
- Expand authoritative nutrition-data coverage beyond the current USDA integration.
- Make food search and logging faster for repeated daily use.
- Add cloud deployment with an appropriate security and privacy model.
- Evaluate authentication and multi-user support only after the single-user workflow is mature.
- Explore more direct mobile health-data integration.

## Project Status

The current milestone delivers the core local nutrition workflow: food-library management, dated meal logging, daily and weekly nutrition summaries, product-photo and USDA imports, duplicate review, and daily energy tracking. The next milestone focuses on screenshots, demo material, and final portfolio polish.
