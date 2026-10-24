# TurnoVivo Backend

API REST de TurnoVivo para gestión de empresas, autenticación, agenda de turnos, reservaciones y listas de espera.

El backend está construido con Spring Boot y utiliza PostgreSQL como almacenamiento persistente, Redis para sesiones y ofertas temporales de turnos, JWT para autenticación y Flyway para controlar la evolución del esquema.

## Contenido

- [Tecnologías](#tecnologías)
- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Configuración local](#configuración-local)
- [Ejecución](#ejecución)
- [Perfiles](#perfiles)
- [Variables de entorno](#variables-de-entorno)
- [Autenticación y roles](#autenticación-y-roles)
- [API REST](#api-rest)
- [Reglas de negocio](#reglas-de-negocio)
- [Calendario de turnos](#calendario-de-turnos)
- [Redis](#redis)
- [Base de datos y migraciones](#base-de-datos-y-migraciones)
- [Manejo de errores](#manejo-de-errores)
- [Pruebas](#pruebas)
- [Docker](#docker)
- [Despliegue](#despliegue)
- [Estructura del proyecto](#estructura-del-proyecto)

## Tecnologías

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje y runtime |
| Spring Boot 4.1.1 | Framework principal |
| Spring MVC | API REST |
| Spring Security | Autorización y filtro JWT |
| Spring Data JPA / Hibernate | Persistencia |
| PostgreSQL | Base de datos relacional |
| Flyway | Migraciones del esquema |
| Redis / Lettuce | Sesiones, holds y eventos de expiración |
| JJWT | Generación y validación de JWT |
| BCrypt | Hash de contraseñas |
| Maven Wrapper | Compilación y pruebas |
| JUnit 5, Mockito y AssertJ | Pruebas automatizadas |
| Docker | Imagen de producción |

## Arquitectura

El proyecto sigue una arquitectura por capas:

```text
HTTP request
    |
    v
Controller
    |
    v
Service (reglas de negocio y transacciones)
    |
    +----> Repository / PostgreSQL
    |
    +----> Redis
```

Responsabilidades principales:

- **Controller:** expone contratos HTTP, valida DTOs y aplica restricciones de rol.
- **Service:** implementa reglas de negocio y límites transaccionales.
- **Repository:** ejecuta consultas mediante Spring Data JPA.
- **Entity:** representa el modelo persistente.
- **DTO:** define solicitudes y respuestas públicas sin exponer entidades JPA.
- **Security:** valida JWT y comprueba que la sesión continúe activa en Redis.
- **Config:** configura CORS, Redis, perfiles, datos demo y seeds de producción.

## Requisitos

Para desarrollo local:

- Java 21.
- PostgreSQL 16 o compatible.
- Redis 7 o compatible.
- Docker opcional.

Comprueba la versión de Java:

```bash
java -version
```

Debe mostrar Java 21. En macOS con Homebrew puede ser necesario configurar:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Configuración local

### 1. PostgreSQL

Crea una base de datos y un usuario. Los valores predeterminados de desarrollo son:

```text
Host: localhost
Port: 5433
Database: turnovivo_db
Username: turnovivo_admin
```

Es recomendable sobrescribir las credenciales mediante un archivo `.env` local.

### 2. Redis

Redis debe estar disponible en:

```text
Host: localhost
Port: 6379
```

El flujo de lista de espera requiere eventos de expiración. Habilítalos con:

```bash
redis-cli CONFIG SET notify-keyspace-events Ex
```

Comprueba el valor:

```bash
redis-cli CONFIG GET notify-keyspace-events
```

### 3. Archivo `.env`

El backend carga automáticamente un `.env` local buscando desde el directorio de trabajo y desde la ubicación de las clases o del JAR. Las variables reales del sistema siempre tienen prioridad.

Ejemplo de desarrollo:

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

No subas `.env` al repositorio.

## Ejecución

### Ejecutar con Maven

```bash
bash ./mvnw spring-boot:run
```

La API inicia de forma predeterminada en:

```text
http://localhost:3000
```

Endpoint básico de salud:

```bash
curl http://localhost:3000/
```

### Construir el JAR

```bash
bash ./mvnw clean package
```

Ejecutar el artefacto:

```bash
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

Después de cambiar código, vuelve a ejecutar `package` antes de probar el JAR. El comando `test` no vuelve a empaquetarlo.

## Perfiles

### `dev`

Es el perfil predeterminado.

- Carga credenciales demo desde variables de entorno.
- Puede crear de forma idempotente usuarios demo.
- Muestra SQL en consola.
- Expone mensajes de error útiles para desarrollo.

Las propiedades demo del perfil `dev` requieren correo y contraseña válidos cuando se configuran.

### `prod`

Actívalo con:

```text
SPRING_PROFILES_ACTIVE=prod
```

Características:

- Oculta consultas SQL.
- Reduce detalles de errores no controlados.
- Permite sembrar el primer `SUPER_ADMIN`.
- Recibe secretos e infraestructura mediante variables de entorno.

## Variables de entorno

Consulta `.env.production.example` como referencia desplegable.

| Variable | Requerida | Descripción |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | Producción | Debe ser `prod` |
| `PORT` | No | Puerto HTTP; valor predeterminado `3000` |
| `DATABASE_URL` | Sí | URL JDBC de PostgreSQL |
| `DB_USERNAME` | Sí | Usuario de PostgreSQL |
| `DB_PASSWORD` | Sí | Contraseña de PostgreSQL |
| `REDIS_HOST` | Sí | Host de Redis |
| `REDIS_PORT` | Sí | Puerto de Redis |
| `REDIS_PASSWORD` | Según proveedor | Contraseña de Redis |
| `REDIS_TLS` | Según proveedor | `true` si Redis requiere TLS |
| `JWT_SECRET` | Sí | Secreto largo y aleatorio para firmar JWT |
| `APP_FRONTEND_URLS` | Sí | Orígenes CORS separados por comas |
| `SEED_SUPER_ADMIN_EMAIL` | Primer despliegue | Correo del primer SUPER_ADMIN |
| `SEED_SUPER_ADMIN_PASSWORD` | Primer despliegue | Contraseña inicial, mínimo 6 caracteres |

Genera un secreto JWT seguro:

```bash
openssl rand -base64 48
```

No cambies `JWT_SECRET` sin considerar que todos los tokens emitidos previamente dejarán de ser válidos.

## Autenticación y roles

El login devuelve un JWT:

```json
{
  "accessToken": "eyJ..."
}
```

Las rutas protegidas requieren:

```http
Authorization: Bearer <token>
```

El filtro de seguridad:

1. Valida la firma y expiración del JWT.
2. Extrae ID, correo y rol.
3. Comprueba que `session:<userId>` continúe activa en Redis.
4. Registra al usuario en el contexto de Spring Security.

El logout revoca la sesión en Redis, por lo que el token deja de autenticar aunque todavía no haya expirado.

### Roles

| Rol | Responsabilidad |
|---|---|
| `SUPER_ADMIN` | Gobierno global, empresas y usuarios ADMIN |
| `ADMIN` | Dueño de una empresa |
| `STAFF` | Personal que atiende turnos |
| `CUSTOMER` | Cliente que reserva y participa en listas de espera |

El registro público siempre crea usuarios `CUSTOMER`, aunque la solicitud incluya otro valor en `role`.

## API REST

### Resumen de acceso

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/` | Público | Comprobación básica del servicio |
| `POST` | `/auth/login` | Público | Iniciar sesión |
| `POST` | `/auth/logout` | Autenticado | Revocar sesión |
| `POST` | `/users` | Público | Registrar cliente |
| `GET` | `/admin/users?role=ADMIN` | `SUPER_ADMIN` | Listar usuarios, opcionalmente por rol |
| `POST` | `/admin/users` | `SUPER_ADMIN` | Crear usuario ADMIN |
| `GET` | `/businesses/{slug}` | Público | Consultar empresa activa por slug |
| `GET` | `/businesses` | `SUPER_ADMIN` | Listar todas las empresas |
| `GET` | `/businesses/id/{id}` | `SUPER_ADMIN` | Consultar empresa por ID |
| `POST` | `/businesses` | `SUPER_ADMIN` | Crear empresa |
| `PUT` | `/businesses/{id}` | `SUPER_ADMIN` | Actualizar empresa |
| `DELETE` | `/businesses/{id}` | `SUPER_ADMIN` | Desactivar empresa |
| `GET` | `/appointments/business/{businessId}/available` | Público | Consultar agenda futura |
| `POST` | `/appointments/book` | `CUSTOMER` | Reservar un slot |
| `POST` | `/appointments/waitlist/join` | `CUSTOMER` | Entrar a lista de espera |
| `POST` | `/appointments/{appointmentId}/cancel` | Autenticado y autorizado | Cancelar cita |
| `POST` | `/appointments/slots/{slotId}/confirm-hold` | `CUSTOMER` | Confirmar oferta temporal |
| `POST` | `/appointments/slots/{slotId}/decline-hold` | `CUSTOMER` | Rechazar oferta |
| `POST` | `/appointments/slots/{slotId}/leave-waitlist` | `CUSTOMER` | Salir de lista de espera |

### Autenticación

#### Iniciar sesión

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

> El contrato actual usa el nombre `passwordHash`, aunque el cliente envía la contraseña en texto plano sobre HTTPS. El backend la compara de forma segura contra el hash BCrypt almacenado.

#### Cerrar sesión

```http
POST /auth/logout
Authorization: Bearer <token>
```

Respuesta: `204 No Content`.

### Usuarios

#### Registrar cliente

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

Respuesta:

```json
{
  "id": "uuid",
  "email": "customer@example.com",
  "role": "CUSTOMER"
}
```

#### Crear ADMIN

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

#### Listar usuarios

```http
GET /admin/users?role=ADMIN
Authorization: Bearer <super-admin-token>
```

El parámetro `role` es opcional. Sin él se devuelven todos los usuarios ordenados por correo.

### Empresas

#### Crear empresa

```http
POST /businesses
Authorization: Bearer <super-admin-token>
Content-Type: application/json
```

```json
{
  "name": "Barbería Centro",
  "slug": "barberia-centro",
  "ownerId": "uuid-de-un-usuario-admin"
}
```

El dueño debe existir y tener rol `ADMIN`. El slug debe ser único, usar minúsculas, números y guiones.

Respuesta administrativa:

```json
{
  "id": "uuid",
  "name": "Barbería Centro",
  "slug": "barberia-centro",
  "ownerId": "uuid",
  "ownerEmail": "owner@example.com",
  "active": true,
  "createdAt": "2026-10-19T09:00:00"
}
```

Al crear la empresa se genera su calendario inicial.

#### Actualizar empresa

```http
PUT /businesses/{id}
Authorization: Bearer <super-admin-token>
Content-Type: application/json
```

```json
{
  "name": "Barbería Centro Renovada",
  "slug": "barberia-centro",
  "ownerId": "uuid-de-un-usuario-admin",
  "active": true
}
```

`active` es opcional. Permite reactivar una empresa previamente desactivada.

#### Desactivar empresa

```http
DELETE /businesses/{id}
Authorization: Bearer <super-admin-token>
```

La eliminación es lógica: cambia `active` a `false`. Responde `204 No Content`.

### Agenda y citas

#### Consultar slots

```http
GET /appointments/business/{businessId}/available
```

Cada slot puede incluir:

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

Estados de slot:

- `AVAILABLE`
- `RESERVED`
- `HOLD`

#### Reservar

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

Respuesta:

```json
{
  "id": "uuid-de-la-cita",
  "customerId": "uuid-del-cliente"
}
```

#### Entrar a lista de espera

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

#### Cancelar cita

```http
POST /appointments/{appointmentId}/cancel
Authorization: Bearer <token>
```

Puede cancelar:

- El cliente propietario de la cita.
- Un usuario `STAFF`.
- El `ADMIN` dueño de la empresa correspondiente.

#### Confirmar, rechazar o abandonar una oferta

```http
POST /appointments/slots/{slotId}/confirm-hold
POST /appointments/slots/{slotId}/decline-hold
POST /appointments/slots/{slotId}/leave-waitlist
Authorization: Bearer <customer-token>
```

Solo el cliente que posee la oferta temporal puede confirmarla o rechazarla.

## Reglas de negocio

### Una reservación activa

Un cliente solo puede tener una cita futura con estado `SCHEDULED`.

Una cita deja de bloquear nuevas reservaciones cuando:

- Se cancela.
- Su fecha y hora ya pasaron.

La validación se ejecuta en el backend tanto al reservar directamente como al confirmar un hold.

### Bloqueo por slot

Cuando un cliente cancela, rechaza o deja expirar una oferta, se registra un `SlotBlock` para esa combinación cliente-slot.

El cliente puede reservar otro horario, incluso el mismo día, pero no volver a interactuar con el slot bloqueado.

Razones posibles:

- `CANCELED`
- `DECLINED`
- `EXPIRED`

### Lista de espera

- Solo se permite entrar cuando el slot no está disponible.
- Un cliente no puede aparecer dos veces en la misma lista.
- La prioridad respeta orden ascendente y fecha de llegada.
- Al liberarse una cita se ofrece el slot al primer cliente.
- La oferta se mantiene durante el TTL configurado.
- Si vence, se bloquea ese cliente para el slot y se promueve al siguiente.

### Concurrencia

Las operaciones críticas cargan usuarios o slots con bloqueo pesimista para reducir reservas simultáneas y estados inconsistentes.

## Calendario de turnos

El servicio genera una ventana móvil de:

- 14 días.
- 16 slots por día.
- Slots de 30 minutos.
- Inicio diario a las 09:00.
- Primer día: mañana.

La reconciliación:

- Se ejecuta a medianoche.
- Solo procesa empresas activas.
- Utiliza el primer usuario `STAFF` disponible.
- Evita duplicados comparando instantes absolutos.
- También se ejecuta al crear una empresa.

Debe existir al menos un usuario `STAFF`; de lo contrario, la generación responde con `503 Service Unavailable`.

## Redis

Redis tiene dos responsabilidades:

### Sesiones

Clave:

```text
session:<userId>
```

Valor:

```text
active
```

TTL predeterminado: 24 horas, igual que la expiración del JWT.

### Holds de lista de espera

Clave:

```text
hold:<slotId>
```

Valor:

```text
<customerId>
```

TTL predeterminado: 30 segundos.

El backend escucha el canal:

```text
__keyevent@*__:expired
```

Redis debe publicar eventos de expiración:

```text
notify-keyspace-events Ex
```

Si esta configuración falta, login y logout pueden funcionar, pero las ofertas vencidas de la lista de espera no se promoverán automáticamente.

## Base de datos y migraciones

Entidades principales:

- `User`
- `Business`
- `AppointmentSlot`
- `Appointment`
- `WaitlistEntry`
- `SlotBlock`

Relaciones relevantes:

- Una empresa tiene un dueño `ADMIN`.
- Un slot pertenece a una empresa y a un miembro `STAFF`.
- Una cita pertenece a un slot único y a un cliente.
- La lista de espera relaciona clientes con slots.
- Los bloqueos evitan que un cliente vuelva a interactuar con un slot penalizado.

Migraciones:

```text
V1__baseline_schema.sql
V2__business_ownership_and_status.sql
V3__user_role_check_superadmin.sql
```

Hibernate usa:

```text
ddl-auto: validate
```

Por tanto, Flyway es la fuente de verdad del esquema.

> **Importante:** nunca modifiques una migración que ya fue aplicada, ni siquiera sus comentarios. Flyway calcula un checksum sobre el archivo completo. Para cambiar el esquema, crea una migración nueva con la siguiente versión.

## Manejo de errores

Los errores controlados usan una respuesta similar a:

```json
{
  "timestamp": "2026-10-19T09:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Descripción del problema."
}
```

Códigos habituales:

| Código | Uso |
|---:|---|
| `200` | Operación correcta |
| `201` | Recurso creado |
| `204` | Operación correcta sin cuerpo |
| `400` | Validación o datos inválidos |
| `401` | Token ausente, inválido, expirado o sesión revocada |
| `403` | Rol o propietario no autorizado |
| `404` | Recurso inexistente |
| `409` | Conflicto con el estado o una regla de negocio |
| `500` | Error interno no controlado |
| `503` | Dependencia funcional no disponible, como ausencia de STAFF |

En producción no se exponen trazas ni detalles internos de errores no controlados.

## Pruebas

Ejecutar toda la suite:

```bash
bash ./mvnw test
```

Ejecutar una clase:

```bash
bash ./mvnw -Dtest=AppointmentServiceTests test
```

Cobertura funcional actual:

- Reservación y límite de una cita activa.
- Cancelación, autorización y promoción de waitlist.
- Expiración de holds.
- Generación idempotente del calendario.
- Gestión y ownership de empresas.
- Slugs duplicados.
- Registro y creación administrativa de usuarios.
- Login correcto e inválido.
- Validación JWT y sesión Redis.
- Restricciones de acceso a endpoints globales.
- Arranque del contexto Spring.

Actualmente la suite contiene 27 pruebas.

## Docker

Construir la imagen:

```bash
docker build -t turnovivo-backend .
```

Ejecutar:

```bash
docker run --rm \
  -p 3000:3000 \
  --env-file .env.production \
  turnovivo-backend
```

La imagen:

- Compila con Java 21 JDK.
- Ejecuta con Java 21 JRE.
- Usa un usuario sin privilegios.
- Activa `prod` de forma predeterminada.

PostgreSQL y Redis deben ser accesibles desde el contenedor. Si se ejecutan en la máquina anfitriona, `localhost` dentro del contenedor no apunta al host.

## Despliegue

Arquitectura prevista:

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

Secuencia recomendada:

1. Desplegar o identificar Redis con soporte para eventos de expiración.
2. Confirmar acceso y permisos de la base Supabase.
3. Configurar secretos en la plataforma del backend.
4. Desplegar la imagen Docker.
5. Revisar que Flyway valide y migre correctamente.
6. Confirmar el seed inicial de `SUPER_ADMIN`.
7. Probar login, logout, reservas y expiración de holds.
8. Establecer en Vercel:

```text
NEXT_PUBLIC_API_URL=https://backend.example.com
```

9. Volver a desplegar el frontend.

### CORS

Configura el dominio principal de Vercel:

```text
APP_FRONTEND_URLS=https://turnovivo.vercel.app
```

Para incluir previews:

```text
APP_FRONTEND_URLS=https://turnovivo.vercel.app,https://*.vercel.app
```

La configuración admite patrones, pero en producción conviene limitar los orígenes al mínimo necesario.

### Seed del primer SUPER_ADMIN

Durante el primer despliegue:

```text
SEED_SUPER_ADMIN_EMAIL=superadmin@example.com
SEED_SUPER_ADMIN_PASSWORD=<strong-password>
```

El seed es idempotente:

- No modifica un usuario ya existente.
- No impide el arranque si las variables no están definidas.
- Una vez creado y probado el usuario, las variables pueden retirarse del proveedor.

## Estructura del proyecto

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
└── README.md
```

## Notas de seguridad

- No almacenes secretos en Git.
- No uses las credenciales demo en producción.
- Usa HTTPS entre el frontend y el backend.
- Usa TLS para conexiones Redis externas cuando el proveedor lo requiera.
- Mantén `JWT_SECRET` estable, largo y aleatorio.
- Restringe CORS a dominios conocidos.
- Conserva contraseñas únicamente como hashes BCrypt.
- No expongas Redis directamente a Internet.

