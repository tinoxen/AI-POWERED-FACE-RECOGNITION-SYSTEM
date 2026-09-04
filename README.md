# CriminalDB — Biometric-Based Record Lookup

CriminalDB is a final-year-project prototype for managing person records and
searching them with face-image similarity. It combines a static browser
frontend, a Spring Boot API, MySQL/H2 persistence, JWT authentication, and a
Python YuNet/ArcFace embedding pipeline.

> **Prototype and responsible-use notice:** This system is intended for
> education, demonstrations, and controlled testing. It uses biometric data
> and must not be used for real law-enforcement decisions without legal,
> ethical, privacy, accuracy, bias, and human-review controls.

## API reference

All protected requests require an `Authorization: Bearer <jwt>` header.

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Public | Authenticate and receive a JWT, username, and role |
| `GET` | `/api/health` | Public | Health check used by Docker and Render |
| `GET` | `/api/persons?q=<text>` | Authenticated | List or search records |
| `GET` | `/api/persons/{id}` | Authenticated | View one record |
| `GET` | `/api/persons/{id}/photo` | Authenticated | Stream a protected photo |
| `POST` | `/api/persons` | `OFFICER`, `ADMIN` | Create a multipart record with a `photo` field |
| `PUT` | `/api/persons/{id}` | `OFFICER`, `ADMIN` | Update record fields as JSON |
| `POST` | `/api/persons/{id}/photo` | `OFFICER`, `ADMIN` | Replace a record photo |
| `DELETE` | `/api/persons/{id}` | `OFFICER`, `ADMIN` | Delete a record |
| `POST` | `/api/persons/match` | Authenticated | Match a multipart query photo; returns up to five results |
| `GET` | `/api/audit` | `ADMIN` | Retrieve audit entries |
| `POST` | `/api/persons/clean-orphans` | `ADMIN` | Remove unreferenced upload files |
| `POST` | `/api/persons/convert-existing-photos` | `ADMIN` | Convert stored photos to WebP |

Login request example:

```json
{
  "username": "admin",
  "password": "your-password"
}
```

Create requests use multipart form data. Required record fields are `fullName`,
`criminalId`, `crimeCategory`, `firNumber`, `policeStation`, `currentStatus`,
`dateOfBirth`, `arrestDate`, and `photo`; the other profile and case fields are
optional. Uploads are limited to 5 MB. A stored photo that cannot produce a
usable face embedding remains available for record viewing but is excluded from
face-match results until the photo is replaced.

## Configuration reference

The main profile reads these environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `10000` | HTTP listening port |
| `DB_HOST`, `DB_PORT` | None | MySQL/TiDB connection host and port |
| `DB_NAME` | None | Database name |
| `DB_USERNAME`, `DB_PASSWORD` | None | Database credentials |
| `APP_JWT_SECRET` | None | Required JWT signing secret |
| `APP_JWT_EXPIRATION_MS` | `3600000` | Token lifetime in milliseconds |
| `APP_UPLOAD_DIR` | `uploads` | Filesystem directory for photos |
| `APP_STATIC_DIR` | `./frontend` | Directory served for static frontend files |
| `ALLOWED_ORIGINS` | Project configuration | Comma-separated CORS origins |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` | None | Optional admin seed credentials |
| `APP_OFFICER_USERNAME`, `APP_OFFICER_PASSWORD` | None | Optional officer seed credentials |

Set secrets through the environment and never commit real passwords, database
credentials, JWT keys, or biometric files. The H2 profile is in-memory and loses
its data when the backend stops.

## Local development workflow

```bash
# MySQL schema/reference setup
cd CriminalDB
mysql -u root -p < database/schema.sql

# Start the API
cd backend
export APP_JWT_SECRET='replace-with-a-long-random-secret'
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD='replace-with-a-strong-password'
./mvnw spring-boot:run

# In another terminal, serve the static frontend
cd CriminalDB/frontend
python3 -m http.server 5500
```

Open `http://localhost:5500/login.html`. The frontend automatically targets
`http://localhost:10000/api` on that local port. The default CORS configuration
allows localhost, loopback, and other local-network origins using port `5500`,
so the app can be opened from another device on the same network. For a
different frontend host or port, set `ALLOWED_ORIGINS` to the exact origin. To
skip MySQL for a disposable demo, start the backend with:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

Run backend tests and package the JAR with:

```bash
./mvnw test
./mvnw clean package
```

The root `package.json` exposes equivalent deployment commands: `npm run build`
builds without tests and `npm start` runs the packaged JAR.

## Face-matching pipeline

`extract_embedding.py` processes every new photo, replacement photo, and query:

1. Checks readability, dimensions, brightness, sharpness, and face count.
2. Detects the face and five landmarks with YuNet.
3. Aligns it to the standard ArcFace 112x112 template.
4. Generates a 512-dimensional, L2-normalized ArcFace embedding.
5. Stores embeddings as comma-separated text and ranks queries by cosine similarity.

Docker downloads the YuNet and ArcFace ONNX models during image build. Local
execution downloads them lazily into `backend/scripts/models/`:

```bash
cd CriminalDB/backend
pip install -r scripts/requirements.txt
python3 scripts/extract_embedding.py /path/to/photo.jpg
```

The similarity cutoff and confidence mapping are prototype tuning choices in
`PersonService`; a similarity score is not proof of identity and must not be
used without an appropriate, consented evaluation and human review process.

## Deployment notes

`CriminalDB/Dockerfile` builds the Spring Boot application, installs Python
inference dependencies, downloads the ONNX models, and serves the frontend and
API from one container. Build it from the `CriminalDB` directory:

```bash
cd CriminalDB
docker build -t criminaldb .
docker run --rm -p 10000:10000 \
  -e APP_JWT_SECRET='replace-with-a-long-random-secret' \
  -e DB_HOST='<mysql-host>' -e DB_PORT=3306 -e DB_NAME=facedb \
  -e DB_USERNAME='<database-user>' -e DB_PASSWORD='<database-password>' \
  criminaldb
```

The included `render.yaml` configures a Render Docker web service and uses
`/api/health` as its health check. Configure the database variables, a strong
JWT secret, admin password, exact `ALLOWED_ORIGINS`, and the database provider's
network allowlist before deploying. Render's example upload directory is
`/tmp/uploads`, which is ephemeral; persistent storage and backups are required
for anything beyond a disposable demo.

## Responsible-use checklist

- Use only synthetic or explicitly consented test images during development.
- Enable HTTPS, least-privilege database permissions, restricted CORS, and secret rotation.
- Add retention, deletion, consent, access-request, and incident-response policies.
- Evaluate false matches, missed matches, demographic performance, and threshold behavior.
- Treat audit logs and biometric embeddings as highly sensitive data.
- Add migrations, rate limiting, monitoring, persistent storage, and independent security review before production use.

CriminalDB is a final-year-project prototype with a real face-embedding pipeline,
not a production law-enforcement system.
## Stack

- **Frontend:** HTML / CSS / vanilla JavaScript
- **Backend:** Java 17 + Spring Boot 3 (Spring Security, Spring Data JPA)
- **Face recognition:** Python + OpenCV (YuNet detector) + ONNX Runtime
  (ArcFace recognizer), invoked as a subprocess from the backend
- **Database:** MySQL/TiDB (an H2 in-memory profile is included for quick demos)
- **Auth:** JWT bearer tokens, BCrypt password hashing, role-based access control
- **Audit:** append-only `audit_logs` table recording logins and record access/changes

## Project structure

```
CriminalDB/
├── backend/            Spring Boot API
│   └── src/main/java/com/facedb/
│       ├── config/      Security config, default-admin seeder
│       ├── controller/  REST endpoints (auth, persons, audit)
│       ├── dto/         Request/response objects
│       ├── model/       JPA entities (User, Person, AuditLog)
│       ├── repository/  Spring Data repositories
│       ├── security/    JWT service + filter
│       └── service/     Business logic, file storage, face-match scoring
│   └── scripts/
│       ├── extract_embedding.py   YuNet detection + ArcFace embedding (called per upload)
│       ├── requirements.txt       Python deps for the script above
│       └── generate_demo_data.py  Seeds 20 demo records through the live API
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
cd CriminalDB
docker build -t criminaldb .
docker run -p 10000:10000 \
  -e PORT=10000 \
  -e APP_JWT_SECRET=local-dev-secret-change-me-please-32chars \
  -e DB_HOST=<mysql-or-tidb-host> -e DB_PORT=4000 -e DB_NAME=facedb \
  -e DB_USERNAME=<user> -e DB_PASSWORD=<password> \
  criminaldb
```

(The Dockerfile lives at `CriminalDB/Dockerfile` and expects `CriminalDB` as
its build context, which is what `render.yaml` also uses.)

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

On first startup the app seeds an admin account only when both
`APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` are configured. It also seeds an
officer account when `APP_OFFICER_USERNAME` and `APP_OFFICER_PASSWORD` are set:

```
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=<strong-random-password>
```

There are no built-in production passwords. Set these credentials through the
environment before startup and rotate them according to your deployment policy.

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

Static assets, including the logo in `frontend/assets/`, are served through the
backend and explicitly permitted by Spring Security. Record photos remain
protected API resources and are loaded with the JWT rather than as public file
paths.

## About the face-matching pipeline

Every photo (new record, updated photo, or a "search by face" query) is
piped through `backend/scripts/extract_embedding.py`, which:

1. **Detects** the face with YuNet (`face_detection_yunet_2023mar.onnx`),
   including basic quality checks (min resolution, brightness, blurriness,
   exactly one face).
2. **Aligns** the face to a canonical 112×112 crop using the 5 landmarks
   YuNet returns (eyes, nose tip, mouth corners) and the standard ArcFace
   reference template.
3. **Embeds** the aligned crop with ArcFace (`w600k_mbf.onnx`, a
   MobileFaceNet backbone trained with the ArcFace/additive-angular-margin
   loss), producing a 512-dimensional, L2-normalized vector.

`PersonService` (Java side) stores that vector as a comma-separated string
per person and, for a match query, ranks stored people by the **cosine
similarity** between their stored embedding and the query embedding
(`calculateCosineSimilarity` / `findTopMatches`). Both models are ONNX
files pulled from public sources by the Dockerfile at build time (or lazily
by the script itself on first run in a local/dev setup), so no proprietary
weights need to be committed to the repo.

This is still a prototype — the confidence-score curve
(`mapSimilarityToScore`), the 75-point cutoff for what counts as a "match",
and the synthetic demo dataset are all tunable/replaceable — but the
detection → alignment → embedding → cosine-similarity pipeline itself is a
real, working face-recognition implementation, not a placeholder.

### Running the embedding script locally (outside Docker)

```bash
cd CriminalDB/backend
pip install -r scripts/requirements.txt
python3 scripts/extract_embedding.py /path/to/a/photo.jpg
```

The first run downloads the YuNet and ArcFace model files into
`backend/scripts/models/` (a few tens of MB total); later runs reuse them.
`PersonService.extractFaceEmbedding()` shells out to this exact script via
`python3`, so `python3` must be on the `PATH` wherever the Spring Boot app
runs (already handled in the Docker image).

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
