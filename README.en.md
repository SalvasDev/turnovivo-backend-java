# TurnoVivo Backend

TurnoVivo REST API for business management, authentication, appointment scheduling, bookings, and waitlists.

The backend is built with Spring Boot and uses PostgreSQL for persistent storage, Redis for sessions and temporary slot offers, JWT for authentication, and Flyway for database schema evolution.

## Contents

- [Technologies](#technologies)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Local configuration](#local-configuration)
- [Running the application](#running-the-application)
- [Profiles](#profiles)
- [Environment variables](#environment-variables)
- [Authentication and roles](#authentication-and-roles)
- [REST API](#rest-api)
- [Business rules](#business-rules)
- [Slot calendar](#slot-calendar)
- [Redis](#redis)
- [Database and migrations](#database-and-migrations)
- [Error handling](#error-handling)
- [Tests](#tests)
- [Docker](#docker)
- [Deployment](#deployment)
- [Project structure](#project-structure)

## Technologies

| Technology | Purpose |
|---|---|
| Java 21 | Language and runtime |
| Spring Boot 4.1.1 | Main framework |
| Spring MVC | REST API |
| Spring Security | Authorization and JWT filtering |
| Spring Data JPA / Hibernate | Persistence |
| PostgreSQL | Relational database |
| Flyway | Schema migrations |
| Redis / Lettuce | Sessions, holds, and expiration events |
| JJWT | JWT generation and validation |
| BCrypt | Password hashing |
| Maven Wrapper | Build and test execution |
| JUnit 5, Mockito, and AssertJ | Automated tests |
| Docker | Production image |

## Architecture

The project follows a layered architecture:

```text
HTTP request
    |
    v
Controller
    |
    v
Service (business rules and transactions)
    |
    +----> Repository / PostgreSQL
    |
    +----> Redis
```

Main responsibilities:

- **Controller:** exposes HTTP contracts, validates DTOs, and applies role restrictions.
- **Service:** implements business rules and transactional boundaries.
- **Repository:** executes queries through Spring Data JPA.
- **Entity:** represents the persistent model.
- **DTO:** defines public requests and responses without exposing JPA entities.
- **Security:** validates JWTs and verifies that sessions remain active in Redis.
- **Config:** configures CORS, Redis, profiles, demo data, and production seeds.

## Requirements

For local development:

- Java 21.
- PostgreSQL 16 or compatible.
- Redis 7 or compatible.
- Docker, optionally.

Check the Java version:

```bash
java -version
```

It must report Java 21. On macOS with Homebrew, you may need:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Local configuration

### 1. PostgreSQL

Create a database and user. The default development values are:

```text
Host: localhost
Port: 5433
Database: turnovivo_db
Username: turnovivo_admin
```

Override credentials through a local `.env` file.

### 2. Redis

Redis must be available at:

```text
Host: localhost
Port: 6379
```

The waitlist flow requires expiration events. Enable them with:

```bash
redis-cli CONFIG SET notify-keyspace-events Ex
```

Verify the value:

```bash
redis-cli CONFIG GET notify-keyspace-events
```

### 3. `.env` file

The backend automatically locates a local `.env` file by searching from the working directory and the class or JAR location. Real operating-system environment variables always take precedence.

Development example:

```dotenv
DATABASE_URL=jdbc:postgresql://localhost:5433/turnovivo_db
DB_USERNAME=turnovivo_admin
DB_PASSWORD=change_me

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=change_me
REDIS_TLS=false

JWT_SECRET=replace_with_a_long_random_secret
APP_FRONTEND_URLS=http://localhost:3001

DEMO_SUPER_ADMIN_EMAIL=superadmin@turnovivo.com
DEMO_SUPER_ADMIN_PASSWORD=change_me
DEMO_ADMIN_EMAIL=admin@turnovivo.com
DEMO_ADMIN_PASSWORD=change_me
DEMO_STAFF_EMAIL=staff@turnovivo.com
DEMO_STAFF_PASSWORD=change_me
DEMO_CUSTOMER_EMAIL=customer@turnovivo.com
DEMO_CUSTOMER_PASSWORD=change_me
```

Never commit `.env` to the repository.

## Running the application

### Run with Maven

```bash
bash ./mvnw spring-boot:run
```

By default, the API starts at:

```text
http://localhost:3000
```

Basic health endpoint:

```bash
curl http://localhost:3000/
```

### Build the JAR

```bash
bash ./mvnw clean package
```

Run the artifact:

```bash
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

After changing code, run `package` again before testing the JAR. The `test` command does not repackage it.

## Profiles

### `dev`

This is the default profile.

- Loads demo credentials from environment variables.
- Can idempotently create demo users.
- Displays SQL in the console.
- Exposes useful development error messages.

The `dev` demo properties require valid email addresses and passwords when configured.

### `prod`

Enable it with:

```text
SPRING_PROFILES_ACTIVE=prod
```

Features:

- Hides SQL queries.
- Reduces details in uncontrolled error responses.
- Supports seeding the first `SUPER_ADMIN`.
- Receives secrets and infrastructure configuration through environment variables.

## Environment variables

Use `.env.production.example` as the deployment reference.

| Variable | Required | Description |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | Production | Must be `prod` |
| `PORT` | No | HTTP port; defaults to `3000` |
| `DATABASE_URL` | Yes | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | PostgreSQL user |
| `DB_PASSWORD` | Yes | PostgreSQL password |
| `REDIS_HOST` | Yes | Redis host |
| `REDIS_PORT` | Yes | Redis port |
| `REDIS_PASSWORD` | Provider-dependent | Redis password |
| `REDIS_TLS` | Provider-dependent | `true` when Redis requires TLS |
| `JWT_SECRET` | Yes | Long, random secret used to sign JWTs |
| `APP_FRONTEND_URLS` | Yes | Comma-separated CORS origins |
| `SEED_SUPER_ADMIN_EMAIL` | Initial deployment | Email for the first SUPER_ADMIN |
| `SEED_SUPER_ADMIN_PASSWORD` | Initial deployment | Initial password, minimum 6 characters |

Generate a secure JWT secret:

```bash
openssl rand -base64 48
```

Do not change `JWT_SECRET` without considering that all previously issued tokens will become invalid.

## Authentication and roles

Login returns a JWT:

```json
{
  "accessToken": "eyJ..."
}
```

Protected routes require:

```http
Authorization: Bearer <token>
```

The security filter:

1. Validates the JWT signature and expiration.
2. Extracts the user ID, email, and role.
3. Confirms that `session:<userId>` remains active in Redis.
4. Registers the user in the Spring Security context.

Logout revokes the Redis session, so the token can no longer authenticate even when it has not yet expired.

### Roles

| Role | Responsibility |
|---|---|
| `SUPER_ADMIN` | Global governance, businesses, and ADMIN users |
| `ADMIN` | Business owner |
| `STAFF` | Staff member who handles appointments |
| `CUSTOMER` | Customer who books and joins waitlists |

Public registration always creates a `CUSTOMER`, even when the request includes a different `role` value.

## REST API

### Access summary

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/` | Public | Basic service check |
| `POST` | `/auth/login` | Public | Log in |
| `POST` | `/auth/logout` | Authenticated | Revoke session |
| `POST` | `/users` | Public | Register a customer |
| `GET` | `/admin/users?role=ADMIN` | `SUPER_ADMIN` | List users, optionally filtered by role |
| `POST` | `/admin/users` | `SUPER_ADMIN` | Create an ADMIN user |
| `GET` | `/businesses/{slug}` | Public | Find an active business by slug |
| `GET` | `/businesses` | `SUPER_ADMIN` | List all businesses |
| `GET` | `/businesses/id/{id}` | `SUPER_ADMIN` | Find a business by ID |
| `POST` | `/businesses` | `SUPER_ADMIN` | Create a business |
| `PUT` | `/businesses/{id}` | `SUPER_ADMIN` | Update a business |
| `DELETE` | `/businesses/{id}` | `SUPER_ADMIN` | Deactivate a business |
| `GET` | `/appointments/business/{businessId}/available` | Public | Read the future schedule |
| `POST` | `/appointments/book` | `CUSTOMER` | Book a slot |
| `POST` | `/appointments/waitlist/join` | `CUSTOMER` | Join a waitlist |
| `POST` | `/appointments/{appointmentId}/cancel` | Authenticated and authorized | Cancel an appointment |
| `POST` | `/appointments/slots/{slotId}/confirm-hold` | `CUSTOMER` | Confirm a temporary offer |
| `POST` | `/appointments/slots/{slotId}/decline-hold` | `CUSTOMER` | Decline an offer |
| `POST` | `/appointments/slots/{slotId}/leave-waitlist` | `CUSTOMER` | Leave a waitlist |

### Authentication

#### Log in

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "email": "customer@example.com",
  "passwordHash": "secret123"
}
```

> The current contract uses the field name `passwordHash`, although the client sends the plain password over HTTPS. The backend securely compares it with the stored BCrypt hash.

#### Log out

```http
POST /auth/logout
Authorization: Bearer <token>
```

Response: `204 No Content`.

### Users

#### Register a customer

```http
POST /users
Content-Type: application/json
```

```json
{
  "email": "customer@example.com",
  "passwordHash": "secret123",
  "role": "CUSTOMER"
}
```

Response:

```json
{
  "id": "uuid",
  "email": "customer@example.com",
  "role": "CUSTOMER"
}
```

#### Create an ADMIN

```http
POST /admin/users
Authorization: Bearer <super-admin-token>
Content-Type: application/json
```

```json
{
  "email": "owner@example.com",
  "password": "secret123"
}
```

#### List users

```http
GET /admin/users?role=ADMIN
Authorization: Bearer <super-admin-token>
```

The `role` parameter is optional. Without it, all users are returned in email order.

### Businesses

#### Create a business

```http
POST /businesses
Authorization: Bearer <super-admin-token>
Content-Type: application/json
```

```json
{
  "name": "Downtown Barbershop",
  "slug": "downtown-barbershop",
  "ownerId": "admin-user-uuid"
}
```

The owner must exist and have the `ADMIN` role. The slug must be unique and contain lowercase letters, numbers, and hyphens.

Administrative response:

```json
{
  "id": "uuid",
  "name": "Downtown Barbershop",
  "slug": "downtown-barbershop",
  "ownerId": "uuid",
  "ownerEmail": "owner@example.com",
  "active": true,
  "createdAt": "2026-10-19T09:00:00"
}
```

The initial calendar is generated when the business is created.

#### Update a business

```http
PUT /businesses/{id}
Authorization: Bearer <super-admin-token>
Content-Type: application/json
```

```json
{
  "name": "Renovated Downtown Barbershop",
  "slug": "downtown-barbershop",
  "ownerId": "admin-user-uuid",
  "active": true
}
```

`active` is optional and can reactivate a previously deactivated business.

#### Deactivate a business

```http
DELETE /businesses/{id}
Authorization: Bearer <super-admin-token>
```

Deletion is logical: it changes `active` to `false`. The response is `204 No Content`.

### Schedule and appointments

#### Read slots

```http
GET /appointments/business/{businessId}/available
```

Each slot may contain:

```json
{
  "id": "uuid",
  "businessId": "uuid",
  "staffId": "uuid",
  "startTime": "2026-10-20T09:00:00-06:00",
  "endTime": "2026-10-20T09:30:00-06:00",
  "status": "AVAILABLE",
  "appointment": null,
  "waitlistEntries": [],
  "slotBlocks": []
}
```

Slot statuses:

- `AVAILABLE`
- `RESERVED`
- `HOLD`

#### Book a slot

```http
POST /appointments/book
Authorization: Bearer <customer-token>
Content-Type: application/json
```

```json
{
  "slotId": "uuid"
}
```

Response:

```json
{
  "id": "appointment-uuid",
  "customerId": "customer-uuid"
}
```

#### Join a waitlist

```http
POST /appointments/waitlist/join
Authorization: Bearer <customer-token>
Content-Type: application/json
```

```json
{
  "slotId": "uuid"
}
```

#### Cancel an appointment

```http
POST /appointments/{appointmentId}/cancel
Authorization: Bearer <token>
```

The following users may cancel:

- The customer who owns the appointment.
- A `STAFF` user.
- The `ADMIN` who owns the corresponding business.

#### Confirm, decline, or leave an offer

```http
POST /appointments/slots/{slotId}/confirm-hold
POST /appointments/slots/{slotId}/decline-hold
POST /appointments/slots/{slotId}/leave-waitlist
Authorization: Bearer <customer-token>
```

Only the customer who owns the temporary offer may confirm or decline it.

## Business rules

### One active booking

A customer may have only one future appointment with `SCHEDULED` status.

An appointment stops blocking new bookings when:

- It is canceled.
- Its date and time have passed.

The backend applies this validation both to direct bookings and hold confirmations.

### Per-slot blocking

When a customer cancels, declines, or allows an offer to expire, a `SlotBlock` is recorded for that customer-slot combination.

The customer may book another time, including another slot on the same day, but cannot interact with the blocked slot again.

Possible reasons:

- `CANCELED`
- `DECLINED`
- `EXPIRED`

### Waitlist

- Customers may join only when the slot is unavailable.
- A customer cannot appear twice on the same waitlist.
- Priority follows ascending order and arrival time.
- When an appointment is released, the slot is offered to the first customer.
- The offer remains active for the configured TTL.
- If it expires, that customer is blocked for the slot and the next customer is promoted.

### Concurrency

Critical operations load users or slots with pessimistic locks to reduce simultaneous bookings and inconsistent states.

## Slot calendar

The service generates a rolling window of:

- 14 days.
- 16 slots per day.
- 30-minute slots.
- Daily opening time at 09:00.
- First generated day: tomorrow.

Reconciliation:

- Runs at midnight.
- Processes active businesses only.
- Uses the first available `STAFF` user.
- Avoids duplicates by comparing absolute instants.
- Also runs when a business is created.

At least one `STAFF` user must exist; otherwise, calendar generation responds with `503 Service Unavailable`.

## Redis

Redis has two responsibilities.

### Sessions

Key:

```text
session:<userId>
```

Value:

```text
active
```

Default TTL: 24 hours, matching the JWT expiration.

### Waitlist holds

Key:

```text
hold:<slotId>
```

Value:

```text
<customerId>
```

Default TTL: 30 seconds.

The backend listens to:

```text
__keyevent@*__:expired
```

Redis must publish expiration events:

```text
notify-keyspace-events Ex
```

Without this configuration, login and logout may work, but expired waitlist offers will not be promoted automatically.

## Database and migrations

Main entities:

- `User`
- `Business`
- `AppointmentSlot`
- `Appointment`
- `WaitlistEntry`
- `SlotBlock`

Relevant relationships:

- A business has an `ADMIN` owner.
- A slot belongs to a business and a `STAFF` member.
- An appointment belongs to one unique slot and one customer.
- Waitlist entries associate customers with slots.
- Blocks prevent customers from interacting with penalized slots again.

Migrations:

```text
V1__baseline_schema.sql
V2__business_ownership_and_status.sql
V3__user_role_check_superadmin.sql
```

Hibernate uses:

```text
ddl-auto: validate
```

Flyway is therefore the schema source of truth.

> **Important:** never modify a migration that has already been applied, including its comments. Flyway calculates a checksum from the complete file. Create a new migration with the next version for every schema change.

## Error handling

Controlled errors use a response similar to:

```json
{
  "timestamp": "2026-10-19T09:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Problem description."
}
```

Common status codes:

| Code | Purpose |
|---:|---|
| `200` | Successful operation |
| `201` | Resource created |
| `204` | Successful operation without a response body |
| `400` | Validation error or invalid input |
| `401` | Missing, invalid, expired token, or revoked session |
| `403` | Unauthorized role or ownership |
| `404` | Resource not found |
| `409` | State or business-rule conflict |
| `500` | Uncontrolled internal error |
| `503` | Functional dependency unavailable, such as missing STAFF |

Production responses do not expose stack traces or internal details for uncontrolled errors.

## Tests

Run the complete suite:

```bash
bash ./mvnw test
```

Run one test class:

```bash
bash ./mvnw -Dtest=AppointmentServiceTests test
```

Current functional coverage:

- Booking and one-active-appointment limit.
- Cancellation, authorization, and waitlist promotion.
- Hold expiration.
- Idempotent calendar generation.
- Business ownership and management.
- Duplicate slugs.
- User registration and administrative creation.
- Successful and invalid login.
- JWT and Redis session validation.
- Global endpoint access restrictions.
- Spring application context startup.

The suite currently contains 27 tests.

## Docker

Build the image:

```bash
docker build -t turnovivo-backend .
```

Run it:

```bash
docker run --rm \
  -p 3000:3000 \
  --env-file .env.production \
  turnovivo-backend
```

The image:

- Builds with Java 21 JDK.
- Runs with Java 21 JRE.
- Uses an unprivileged user.
- Enables the `prod` profile by default.

PostgreSQL and Redis must be reachable from the container. If they run on the host machine, `localhost` inside the container does not point to the host.

## Deployment

Target architecture:

```text
Vercel frontend
       |
       | HTTPS
       v
Spring Boot backend
       |
       +----> Supabase PostgreSQL
       |
       +----> Managed Redis
```

Recommended sequence:

1. Deploy or identify a Redis service with expiration-event support.
2. Confirm Supabase access and permissions.
3. Configure secrets on the backend platform.
4. Deploy the Docker image.
5. Confirm that Flyway validates and migrates successfully.
6. Confirm the initial `SUPER_ADMIN` seed.
7. Test login, logout, booking, and hold expiration.
8. Set the following in Vercel:

```text
NEXT_PUBLIC_API_URL=https://backend.example.com
```

9. Redeploy the frontend.

### CORS

Configure the main Vercel domain:

```text
APP_FRONTEND_URLS=https://turnovivo.vercel.app
```

To include previews:

```text
APP_FRONTEND_URLS=https://turnovivo.vercel.app,https://*.vercel.app
```

The configuration supports patterns, but production deployments should allow only the minimum required origins.

### Seed the first SUPER_ADMIN

During the first deployment:

```text
SEED_SUPER_ADMIN_EMAIL=superadmin@example.com
SEED_SUPER_ADMIN_PASSWORD=<strong-password>
```

The seed is idempotent:

- It does not modify an existing user.
- Missing variables do not prevent startup.
- The variables may be removed from the provider after the user is created and tested.

## Project structure

```text
turnovivo-backend-java/
├── Dockerfile
├── pom.xml
├── .env.production.example
├── src/
│   ├── main/
│   │   ├── java/com/saldev/turnovivo/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── exception/
│   │   │   ├── mapper/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   └── service/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/
│   └── test/
│       └── java/com/saldev/turnovivo/
├── README.md
└── README.en.md
```

## Security notes

- Never store secrets in Git.
- Do not use demo credentials in production.
- Use HTTPS between the frontend and backend.
- Use TLS for external Redis connections when required by the provider.
- Keep `JWT_SECRET` stable, long, and random.
- Restrict CORS to known domains.
- Store passwords only as BCrypt hashes.
- Do not expose Redis directly to the Internet.

