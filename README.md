# Moniepoint KV Spring Boot

> A lightweight, Bitcask‑style key–value (KV) store implemented with Java Spring Boot based on the research paper. Supports basic CRUD on keys and persistent on‑disk storage. The service is self‑contained, runs on an embedded Tomcat server and can be packaged as a JAR or Docker image.

---

## 🚀 Quick Start

### Prerequisites

* **Java 17+** (required to run the JAR or build from source.)
* **Maven 3** (only required if you need to compile the project from source.)
* **Docker** (optional, used for running the prebuilt image.)

### Run with Docker (recommended)

> Run the below command from the root directory of the project.
> Make sure that [docker-compose.yml](https://github.com/HasnainRabbani/moniepoint-kv-springboot/blob/main/docker-compose.yml) file is present.

```bash
docker compose up -d
```

### Run locally (no Docker)

```bash
# 1) Build the JAR
mvn clean package -DskipTests

# 2) Run
java -jar target/*.jar \
  --kv.dataDir=./data \
  --kv.syncMode=ALWAYS \
  --server.port=8080
```

### Run directly from source

```bash
git clone git@github.com:HasnainRabbani/moniepoint-kv-springboot.git
cd moniepoint-kv-springboot
mvn clean package -DskipTests
```

### Run Swagger UI
> After startup, open **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
---

## 📜 API Overview

Use **Swagger UI** to explore and try endpoints:

* Swagger UI: `http://localhost:8080/swagger-ui/index.html`
* OpenAPI JSON: `http://localhost:8080/v3/api-docs` (Optional)

### Endpoints

* `GET  /kv/v1/health` — health check returns OK (means: server is up and running)

* `GET  /kv/v1/{key}`     — read value by key
* `PUT  /kv/v1/{key}`     — put / overwrite value for key
* `DELETE /kv/v1/{key}`   — delete a key
* `POST /kv/v1/batchPut`  — batch put values for keys (request body as JSON)
* `GET  /kv/v1/range`     — read key/value pairs in (start, end)

### Example cURL

```bash
# health check
curl -X 'GET' \
  'http://localhost:8080/kv/v1/health' \
  -H 'accept: */*'

# read value by key
curl -X 'GET' \
  'http://localhost:8080/kv/v1/k5' \
  -H 'accept: text/plain'

# put / overwrite value for key
curl -X 'PUT' \
  'http://localhost:8080/kv/v1/k5' \
  -H 'accept: */*' \
  -H 'Content-Type: text/plain' \
  -d 'moniepoint'

# delete a key
curl -X 'DELETE' \
  'http://localhost:8080/kv/v1/k5' \
  -H 'accept: */*'```

# batch put values for keys (request body as JSON)
curl -X 'POST' \
  'http://localhost:8080/kv/v1/batchPut' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '[
  {
    "key": "k1",
    "value": "v1"
  },
  {
    "key": "k2",
    "value": "v2"
  },
  {
    "key": "k3",
    "value": "v3"
  }
]'

# read key/value pairs in (start, end)
curl -X 'GET' \
  'http://localhost:8080/kv/v1/range?start=k1&end=k9' \
  -H 'accept: text/plain'
```
---
## ⚙️ Configuration

Configuration can be provided via **`application.yml`**, **env vars**, or **CLI args**.

### Common properties (Env variable `KvConfig`)

> Update the property names if your config class differs.

| Property                   | Env Var                      | Description                      | Default  |
| -------------------------- | ---------------------------- | -------------------------------- | -------- |
| `kv.dataDir`               | `KV_DATA_DIR`                | Directory for Bitcask files      | `./data` |
| `kv.syncMode`              | `KV_SYNC_MODE`               | `ALWAYS`, `BATCH`                | `ALWAYS` |
| `kv.batchSyncEvery`        | `KV_BATCH_SYNC_EVERY`        | Flush every N ops (BATCH)        | `100`    |
| `kv.syncIntervalMs`        | `KV_SYNC_INTERVAL_MS`        | Flush interval ms (INTERVAL)     | `50`     |
| `kv.compactThresholdBytes` | `KV_COMPACT_THRESHOLD_BYTES` | Trigger compaction               | `0`      |
| `server.port`              | `SERVER_PORT`                | HTTP port                        | `8080`   |

---

## 🧪 Testing

```bash
# run unit tests
mvn test
```

---

## 🏗️ Build & Packaging

```bash
# build jar
mvn clean package

# resulting artifact (adjust if your artifactId differs)
ls target/
```

To produce a new Docker image (if you have a Dockerfile):

```bash
docker build -t moniepoint/kv:latest .
```

---

## 🧾 Swagger & Postman

* **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

### Postman collection

1. In Postman: **Import** → select `Moniepoint KV.postman_collection.json` → it generates a collection.
2. Upon successful import, all the available endpoints will be there.
3. Please note: add the environment variable: `http://localhost:8080/kv/v1` as a `base_url`.

---

## 📦 Data & Persistence Tips

* Ensure `kv.dataDir` points to a **writable** path. On Windows, prefer a short absolute path without spaces (e.g., `C:\kv-data`).
* When running in Docker on Windows, mount a path like `/c/kv-data:/data` instead of a path containing spaces.
* To inspect on-disk files, stop the app first to avoid partial writes during compaction.

---

## 🔍 Troubleshooting

* **`Permission denied (publickey)` on Git:** verify your SSH key setup and `~/.ssh/config`.
* **Port already in use:** change `server.port` or free `8080`.

---

## 🧱 Architecture (high level)

* **API Layer:** Spring Web controllers exposing KV operations
* **Service Layer:** KV operations (put/get/delete/post)
* **Store Layer:** Bitcask‑style append‑only log + in‑memory index
* **Compaction:** background process based on `compactThresholdBytes`

> High Level Overview.
<img width="658" height="700" alt="image" src="https://github.com/user-attachments/assets/c3d4fbd5-123e-4e6a-b3b8-7668035b95f5" />

---

## 🗂️ Project Layout (example)

```
src/
  main/
    java/com/moniepoint/kv/
      KvSpringBootApplication.java
      config/
        KvConfig.java
        SwaggerConfig.java
        KvProperties.java
      controller/
        KvController.java
      model/
        Bytes.java
        Crc32s.java
        KvEntry.java
        KvPair.java
        Position.java
      service/
        KvService.java
        impl/
          KvServiceImpl.java
      util/
        BitcaskStore.java
        SegmentFile.java
        Utils.java
application.yml
docker-compose.yml
Dockerfile
Moniepoint KV.postman_collection.json
```
