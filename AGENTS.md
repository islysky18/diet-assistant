# Project Overview

This project is a single-user personal diet assistant.

The application helps the user:
- manage foods currently available at home
- record daily meals
- track calories and nutrition
- store basic health metrics
- receive personalized meal recommendations
- adjust recommendations based on workout status and foods already consumed

This is currently a personal application for exactly one user.

# Technology Stack

- Java 21
- Spring Boot
- Maven
- Spring MVC
- Thymeleaf
- HTMX
- Spring Data JPA
- PostgreSQL
- Flyway
- Bean Validation
- JUnit 5
- Testcontainers

# Architecture

Use a modular monolith organized by feature.

Main feature packages:

- profile
- health
- food
- pantry
- meal
- nutrition
- recommendation
- ai

Controllers must not access repositories directly.

Expected flow:

Controller
-> Service
-> Repository

Business logic must be placed in services or dedicated domain classes.

# Product Constraints

- The application supports exactly one user.
- Do not implement authentication.
- Do not implement authorization.
- Do not implement multi-tenancy.
- Do not implement payments or subscriptions.
- Do not implement mobile applications yet.
- Do not implement microservices.
- Keep the architecture easy to extend later.

# Nutrition Rules

- Nutrition totals must be calculated by Java code.
- Do not trust AI-generated nutrition totals.
- Use nutrition data stored in the application database.
- AI recommendations may only use foods that exist in the pantry.
- AI recommendations must not automatically create meal records.
- The user must confirm a meal before it is recorded.
- Use BigDecimal for food quantity and nutrition values.

# Medical Safety

- Health metrics are user-provided context.
- The application must not diagnose medical conditions.
- The application must not recommend changing medications.
- Recommendations should be presented as general nutrition guidance.
- Important medical concerns should advise consulting a qualified healthcare professional.

# Coding Rules

- Use Java 21.
- Use constructor injection.
- Do not use field injection.
- Do not use Lombok.
- Use records for immutable DTOs when appropriate.
- Use LocalDate, LocalTime, and LocalDateTime for dates and times.
- Add Bean Validation to request DTOs.
- Do not expose JPA entities directly from controllers.
- Add unit tests for business logic.
- Add integration tests for important database behavior.
- Do not add a dependency unless it is necessary.
- Explain new dependencies before adding them.
- Prefer simple and readable code over abstraction.
- Run tests after making changes.

# UI Engineering Rules

- Prefer component-specific classes for text inputs, checkboxes, radios, and selects over generic `input` tag selectors.
- Text-input sizing must not unintentionally apply to checkbox or radio controls. A selector such as `.form-field input` must explicitly exclude checkbox and radio types, or be replaced with a component-specific selector.
- Verify every HTML/CSS layout change at both baseline viewports: desktop `1280 x 800` and mobile `390 x 844`.
- Treat no horizontal page overflow, reasonably sized checkbox/radio controls, visible controls and primary actions, no obvious control overlap, and usable desktop/mobile layouts as objective correctness checks.
- Keep objective UI regression checks separate from subjective visual design review; avoid brittle pixel-perfect assertions unless a stable visual baseline is intentional.

# Working Process

Before implementing a task:

1. Read this AGENTS.md file.
2. Inspect the existing project structure.
3. Explain the proposed changes briefly.
4. Implement only the requested scope.
5. Run relevant tests.
6. Report the files changed and test results.

Do not implement unrelated future features.
