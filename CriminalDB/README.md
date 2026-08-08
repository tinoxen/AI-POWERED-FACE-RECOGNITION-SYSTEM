# FaceDB — Biometric-Based Record Lookup (FYP Prototype)

A prototype web application for managing person records with a face
photo, built for a final-year project. **Uses synthetic/mock data and a
simulated biometric matching step, not a real production face-recognition
or law-enforcement system.**

## Stack

- **Frontend:** HTML / CSS / vanilla JavaScript
- **Backend:** Java 17 + Spring Boot 3 (Spring Security, Spring Data JPA)
- **Database:** MySQL (an H2 in-memory profile is included for quick demos)
- **Auth:** JWT bearer tokens, BCrypt password hashing, role-based access control
- **Audit:** append-only `audit_logs` table recording logins and record access/changes

## Project structure

```
FaceDB/
├── backend/            Spring Boot API
│   └── src/main/java/com/facedb/
│       ├── config/      Security config, default-admin seeder
│       ├── controller/  REST endpoints (auth, persons, audit)
│       ├── dto/         Request/response objects
│       ├── model/       JPA entities (User, Person, AuditLog)
│       ├── repository/  Spring Data repositories
│       ├── security/    JWT service + filter
│       └── service/     Business logic, file storage, mock face matching
├── frontend/            Static HTML/CSS/JS pages
│   ├── login.html
│   ├── dashboard.html
│   ├── add-person.html
│   ├── view-persons.html
│   ├── edit-person.html (admin)
│   └── audit-logs.html  (admin)
├── database/
│   └── schema.sql       Reference MySQL schema
└── uploads/              Face photos land here at runtime
```

## Running it

### Docker / Render deployment

The project now includes a Dockerfile and Render configuration so it can be hosted as a single web service.

```bash
docker build -f backend/Dockerfile -t criminaldb .
docker run -p 10000:10000 -e PORT=10000 criminaldb
```

For Render, connect the repository and create a new Web Service using the
included [render.yaml](render.yaml). Render builds the container and serves
both the API and frontend from the generated URL. The health check is
`/api/health`.

Before the first deploy, create a **Serverless** TiDB Cloud cluster and a
database (for example, `facedb`). In the Render dashboard, set these required
environment variables from TiDB Cloud's **Connect** dialog:

| Render variable | TiDB Cloud value |
| --- | --- |
| `DB_HOST` | Host (without `https://`) |
| `DB_PORT` | `4000` unless TiDB Cloud shows another port |
| `DB_NAME` | The database name, e.g. `facedb` |
| `DB_USERNAME` | TiDB Cloud SQL user |
| `DB_PASSWORD` | That SQL user's password |
| `APP_JWT_SECRET` | A random secret of at least 32 characters |

TiDB Cloud requires TLS; the supplied JDBC URL enforces certificate and host
verification. Add Render's outbound IP ranges to the cluster's IP allowlist,
or temporarily allow all IPs for an initial demo. After the service is live,
replace `https://criminaldb.onrender.com` in `ALLOWED_ORIGINS` with the actual
Render URL if it differs.

### 1. Backend

Set up MySQL (or skip this and use the H2 profile below):

```bash
mysql -u root -p < database/schema.sql
```

Edit `backend/src/main/resources/application.properties` if your DB
credentials differ from the defaults, then:

```bash
cd backend
mvn spring-boot:run
```

To try it without MySQL first, run with the in-memory H2 profile instead:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

On first startup the app seeds a default admin account:

```
username: admin
password: 5623
```

**Change this password immediately** — it's only there so the app is usable
out of the box.

### 2. Frontend

The frontend is static, so any local web server works, e.g.:

```bash
cd frontend
python3 -m http.server 5500
```

Then open `http://localhost:5500/login.html`. The CORS config in
`SecurityConfig.java` already allows `http://localhost:5500`; update it if
you serve the frontend elsewhere.

## Roles

| Role    | Can do |
|---------|--------|
| VIEWER  | Log in, view/search records |
| OFFICER | Everything VIEWER can, plus add new records |
| ADMIN   | Everything OFFICER can, plus edit/delete records and view audit logs |

## About the "biometric" part

`PersonService.mockFaceprint()` and `topMatches()` are placeholders: they
hash the uploaded image and compare hashes, which demonstrates the
end-to-end workflow (capture → store → compare → retrieve) without
implementing real face recognition. For the next milestone, swap that
method's internals for calls to an actual face-embedding model (e.g. a
FaceNet/ArcFace-based service) and compare real embedding vectors with
cosine similarity instead of the current Hamming-distance stand-in — the
rest of the app (schema, endpoints, audit logging, RBAC) doesn't need to
change.

## Security notes for the write-up

- Passwords are hashed with BCrypt, never stored in plaintext.
- Auth uses short-lived JWTs; the secret in `application.properties` is a
  placeholder and must be replaced with a real secret (e.g. via an
  environment variable) before any real deployment.
- Role-based access control is enforced both at the Spring Security filter
  chain level and with `@PreAuthorize` on sensitive endpoints.
- The audit log is append-only by design at the application layer; in a
  real deployment you'd also revoke UPDATE/DELETE grants on that table for
  the app's DB user.
- File uploads are restricted by type and size in `FileStorageService`.

This is a solid base to demo and to describe in your proposal as a
prototype suitable for pitching, with a clear, explainable path to a real
biometric-matching component later.
