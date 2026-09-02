# CriminalDB — Biometric-Based Record Lookup (FYP Prototype)

A prototype web application for managing person records with a face
photo, built for a final-year project. **Uses synthetic demo data by
default; it is a prototype, not a real production law-enforcement system.**
Face matching itself is real: photos are compared with actual ArcFace
embeddings, not a placeholder.

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
