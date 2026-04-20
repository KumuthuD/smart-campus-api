# Smart Campus Sensor & Room Management API

> **Module:** 5COSC022W – Client-Server Architectures | University of Westminster  
> **Technology:** Java 11 · JAX-RS (Jersey 3.1.3) · Grizzly HTTP Server · Jackson JSON  
> **Base URL:** `http://localhost:8080/api/v1`

---

## Overview

This project is a fully RESTful API built using **JAX-RS (Jersey 3.1.3)** and an embedded **Grizzly HTTP server** for the University of Westminster's "Smart Campus" initiative. The API provides a comprehensive interface for campus facilities managers and automated building systems to manage **Rooms** and **IoT Sensors** (CO2 monitors, occupancy trackers, smart lighting controllers) across campus.

### Key Design Principles
- **RESTful architecture** with proper HTTP verbs, status codes, and resource hierarchy
- **In-memory data storage** using `ConcurrentHashMap` for thread-safe access (no database)
- **Logical resource nesting** reflecting the physical campus structure (`/sensors/{id}/readings`)
- **Zero stack trace leakage** — all errors return structured JSON bodies via `ExceptionMapper`
- **Full observability** via a JAX-RS `ContainerRequestFilter`/`ContainerResponseFilter` logging filter

### Technology Stack
| Component | Technology |
|-----------|-----------|
| Language | Java 11 |
| REST Framework | JAX-RS via Jersey 3.1.3 |
| HTTP Server | Grizzly2 (embedded, no external server needed) |
| JSON Serialisation | Jackson (via `jersey-media-json-jackson`) |
| Build Tool | Apache Maven with `maven-shade-plugin` (fat JAR) |
| Data Storage | `ConcurrentHashMap` + `ArrayList` (in-memory only) |

---

## How to Build and Run

### Prerequisites
- **Java 11** or higher (`java -version` to verify)
- **Apache Maven 3.6+** (`mvn -version` to verify)
- Internet connection (first build downloads dependencies from Maven Central)

### Step 1 — Clone the Repository
```bash
git clone https://github.com/KumuthuD/smart-campus-api.git
cd smart-campus-api
```

### Step 2 — Build the Fat JAR
```bash
mvn clean package -q
```
This compiles all sources and packages them with all dependencies into a single executable JAR at `target/smart-campus-api-1.0-SNAPSHOT.jar`.

### Step 3 — Run the Server
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```

**Expected console output:**
```
===========================================
Smart Campus API running at: http://localhost:8080/api/v1
Press ENTER to stop the server...
===========================================
```

### Step 4 — Verify It Is Running
```bash
curl -X GET http://localhost:8080/api/v1/
```
You should receive a `200 OK` JSON response with API metadata.

### Step 5 — Stop the Server
Press `ENTER` in the terminal where the server is running.

---

## Sample curl Commands

### 1. API Discovery
```bash
curl -X GET http://localhost:8080/api/v1/
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LIB-301","name":"Library Quiet Study","capacity":50}'
```

### 3. Get All Rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```

### 4. Get a Specific Room by ID
```bash
curl -X GET http://localhost:8080/api/v1/rooms/LIB-301
```

### 5. Register a New Sensor (validates that the roomId exists)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-001","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}'
```

### 6. Filter Sensors by Type (case-insensitive)
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=CO2"
```

### 7. Add a Sensor Reading (also updates the parent sensor's currentValue)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":412.5}'
```

### 8. Get Full Reading History for a Sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/CO2-001/readings
```

### 9. Attempt to Register a Sensor with a Non-Existent roomId (returns 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-999","type":"Temperature","status":"ACTIVE","currentValue":0.0,"roomId":"INVALID-ROOM"}'
```

### 10. Attempt to Delete a Room That Has Sensors (returns 409)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

---

## API Design Overview

### Resource Hierarchy

```
http://localhost:8080/api/v1
│
├── /                              GET  — API discovery & metadata (HATEOAS links)
│
├── /rooms                         GET  — List all rooms
│                                  POST — Create a new room (returns 201 + Location header)
│
├── /rooms/{roomId}                GET  — Get a specific room by ID
│                                  DELETE — Delete room (blocked with 409 if it has sensors)
│
├── /sensors                       GET  — List all sensors (optional ?type= filter)
│                                  POST — Register a new sensor (validates roomId, returns 201)
│
├── /sensors/{sensorId}            GET  — Get a specific sensor by ID
│
└── /sensors/{sensorId}/readings   GET  — Get full reading history for a sensor
                                   POST — Add a new reading (updates parent sensor's currentValue)
```

### HTTP Status Codes

| Code | Meaning | When Used in This API |
|------|---------|----------------------|
| `200 OK` | Success | Successful `GET` requests |
| `201 Created` | Resource created | Successful `POST` (room, sensor, or reading) — includes `Location` header |
| `204 No Content` | Success, no body | Successful `DELETE` of a room |
| `400 Bad Request` | Invalid input | Missing required fields (e.g., no `id` in POST body) |
| `403 Forbidden` | Action not allowed | POSTing a reading to a sensor with status `"MAINTENANCE"` |
| `404 Not Found` | Resource missing | GET/DELETE on a non-existent room or sensor |
| `409 Conflict` | Business constraint | Attempting to DELETE a room that still has sensors assigned |
| `415 Unsupported Media Type` | Wrong content type | Sending `text/plain` or `application/xml` to a JSON endpoint |
| `422 Unprocessable Entity` | Semantic error | Registering a sensor with a `roomId` that does not exist |
| `500 Internal Server Error` | Unexpected failure | Any unhandled runtime exception (stack trace never exposed externally) |

### Project File Structure

```
smart-campus-api/
├── pom.xml
├── .gitignore
├── README.md
└── src/main/java/com/smartcampus/
    ├── Main.java                                  ← Grizzly server startup
    ├── SmartCampusApp.java                        ← @ApplicationPath("/api/v1")
    ├── DataStore.java                             ← Shared ConcurrentHashMap storage
    ├── model/
    │   ├── Room.java
    │   ├── Sensor.java
    │   └── SensorReading.java
    ├── resource/
    │   ├── DiscoveryResource.java                 ← GET /api/v1
    │   ├── RoomResource.java                      ← GET/POST/DELETE /rooms
    │   ├── SensorResource.java                    ← GET/POST /sensors + sub-resource locator
    │   └── SensorReadingResource.java             ← GET/POST /sensors/{id}/readings
    ├── exception/
    │   ├── RoomNotEmptyException.java             ← Thrown on: DELETE room with sensors
    │   ├── RoomNotEmptyExceptionMapper.java       ← Maps to: 409 Conflict
    │   ├── LinkedResourceNotFoundException.java   ← Thrown on: POST sensor with invalid roomId
    │   ├── LinkedResourceNotFoundExceptionMapper.java ← Maps to: 422 Unprocessable Entity
    │   ├── SensorUnavailableException.java        ← Thrown on: POST reading to MAINTENANCE sensor
    │   ├── SensorUnavailableExceptionMapper.java  ← Maps to: 403 Forbidden
    │   └── GlobalExceptionMapper.java             ← Catch-all: maps Throwable to 500
    └── filter/
        └── LoggingFilter.java                     ← Logs all requests and responses
```

---

## Report: Question Answers

---

### Part 1.1 — JAX-RS Resource Lifecycle & Thread Safety

**Question:** Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created for every incoming request, or does the runtime treat it as a singleton? How does this architectural decision impact the way you manage and synchronise your in-memory data structures?

**Answer:**

By default, JAX-RS resource classes are **request-scoped**: the runtime instantiates a brand-new instance of each resource class for every incoming HTTP request. After the request is processed and the response sent, that instance is discarded and eligible for garbage collection. This is the direct opposite of a singleton, where a single object services all requests for the lifetime of the application.

This lifecycle has a critical consequence for shared state management. Because each instance is freshly created per request, any data written to an **instance field** is completely lost when that instance is discarded — it will not be seen by the next request. To share data across requests (and across the many resource class instances being created), state must be stored in **static fields** that belong to the class itself, not to any particular instance. This is the architectural reason behind the `DataStore` class with its `public static final` maps: every resource instance, regardless of which thread or request it serves, reads and writes to the same shared static maps.

However, static shared state introduces a **concurrency hazard**. When multiple HTTP requests arrive simultaneously, multiple resource instances execute concurrently on different threads. If two threads simultaneously call `.put()` on a standard `HashMap` or `.add()` on an `ArrayList`, the result is undefined behaviour — data corruption, lost updates, or a `ConcurrentModificationException`. To eliminate this, this implementation uses `ConcurrentHashMap`, which is a purpose-built thread-safe map that handles concurrent reads and writes internally without requiring explicit `synchronized` blocks. Additionally, `computeIfAbsent` is used as an atomic operation when initialising readings lists, ensuring that the check-then-act sequence itself cannot be interrupted by another thread — making the initialisation race-condition-free.

---

### Part 1.2 — HATEOAS and Hypermedia in RESTful APIs

**Question:** Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

**Answer:**

HATEOAS — Hypermedia as the Engine of Application State — is the principle that API responses should embed navigational links to related resources and available operations, enabling clients to discover and traverse the API dynamically without relying on prior external knowledge. It represents the highest level (Level 3) of the Richardson Maturity Model for REST APIs.

The fundamental problem with static documentation is **coupling and staleness**. A client developer reads a wiki or PDF, hardcodes the URL `POST /api/v1/rooms` into their application, and deploys it. Six months later, the API is versioned to `/api/v2/rooms`. Every client that hardcoded the old URL now silently breaks. The documentation may not be updated in time, and there is no in-band signal to the client that something has changed.

HATEOAS solves this through **discoverability and reduced coupling**. A client starts at a single, stable entry point — `GET /api/v1` — and receives links to every top-level resource (`"rooms": "/api/v1/rooms"`, `"sensors": "/api/v1/sensors"`). The client's logic depends on link relation names, not hardcoded paths. If a URL changes, the server updates the links in its responses and all clients that follow those links adapt automatically. New resources can be introduced and existing clients discover them at runtime without requiring documentation updates or client code changes. This is especially valuable in long-lived APIs consumed by multiple independent client teams, where the contract inevitably evolves over time.

---

### Part 2.1 — Returning IDs vs Full Objects in List Responses

**Question:** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing.

**Answer:**

**Returning full room objects** in the list response reduces the number of HTTP round trips to one. A client that receives the complete list can immediately render all room data — names, capacities, sensor counts — without making N additional `GET /rooms/{id}` requests. This is the optimal approach for use cases where clients typically need all fields, such as displaying a room management dashboard. The trade-off is payload size: for very large collections (thousands of rooms), transmitting every field for every room consumes significant bandwidth even when the client only needs two or three fields per item.

**Returning only IDs** minimises the initial response payload and is appropriate when clients need very limited information upfront (e.g., a dropdown list showing only room names, populated lazily). However, it creates the well-known **N+1 query problem**: to retrieve full details for a list of 500 rooms, the client must make 501 HTTP requests (1 for the ID list + 500 individual fetches). Each of those requests incurs network latency, serialisation overhead, and server processing cost, making the total time dramatically worse than a single complete response.

The best practice for production APIs is to return full objects for moderate-to-small collections, and to support **pagination** (e.g., `?page=1&size=20`) and **field projection** (e.g., `?fields=id,name`) for large datasets. For this coursework, returning full room objects in all list responses is the correct and expected approach and is what the "Excellent" rubric criteria requires.

---

### Part 2.2 — Idempotency of DELETE

**Question:** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

**Answer:**

Yes, the DELETE operation in this implementation is **idempotent**, fully consistent with the HTTP specification (RFC 7231, Section 4.2.2). Idempotency means that issuing the same request N times produces the same **server state** as issuing it once — although the HTTP response code may differ across calls.

Here is the exact sequence of server state changes:

- **First DELETE `/api/v1/rooms/LIB-301`:** The room exists and has no sensors assigned → the implementation removes it from `DataStore.rooms` using `ConcurrentHashMap.remove()` → responds `204 No Content`. Server state: room `LIB-301` does not exist.
- **Second DELETE `/api/v1/rooms/LIB-301`:** The room is not found in `DataStore.rooms` → the implementation responds `404 Not Found`. Server state: room `LIB-301` still does not exist — **unchanged**.
- **Third and subsequent DELETEs:** Identical to the second call — `404 Not Found`. Server state remains unchanged.

The server state after all calls is identical — the room does not exist. This is the definition of idempotency: the side effect (removing the resource) is produced exactly once, no matter how many times the request is made. The change in response code (204 → 404) is expected and acceptable under the HTTP spec — idempotency is about server state, not response identity.

This property is critically important for reliability in distributed systems. If a client sends a DELETE request over an unreliable network and the response is lost (not the request itself), the client cannot know whether the deletion succeeded. It is safe to retry — if the room was already deleted, the server correctly returns 404, which the client can treat as confirmation of success. Without idempotency, retrying could produce unintended side effects.

---

### Part 3.1 — `@Consumes` and Media Type Mismatches

**Question:** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

**Answer:**

The `@Consumes(MediaType.APPLICATION_JSON)` annotation registers a **pre-dispatch content negotiation constraint** with the JAX-RS runtime. When a request arrives, the runtime inspects the `Content-Type` request header before selecting a method to invoke. If the header value does not match any of the media types declared in `@Consumes` for the available methods, the runtime cannot find a matching handler and automatically generates an **HTTP 415 Unsupported Media Type** response — before a single line of application code executes.

This is a framework-level enforcement mechanism, not application logic. The error is generated by the Jersey message body reader selection algorithm, which walks the list of registered `MessageBodyReader` implementations and finds no reader capable of deserialising the incoming `text/plain` or `application/xml` content into the target Java type (`Room`, `Sensor`, etc.). The JAX-RS specification mandates that 415 is returned in this situation.

There is an important distinction to understand: if the `Content-Type` header is correctly set to `application/json` but the body contains **malformed JSON** (e.g., missing a closing brace), Jackson's deserialiser throws a `JsonParseException` or `JsonMappingException` during the parameter binding phase. This exception is not automatically a 415 — it bubbles up as an unmapped exception and would be intercepted by the `GlobalExceptionMapper`, returning a 500. A production-grade implementation would include a specific `ExceptionMapper<JsonMappingException>` to return a more informative 400 Bad Request in that case.

---

### Part 3.2 — `@QueryParam` vs Path-Based Filtering

**Question:** You implemented filtering using `@QueryParam`. Contrast this with an alternative design using path parameters (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

**Answer:**

The distinction is semantic and architectural. **Path parameters** identify a specific, named resource within a hierarchy — they are part of the resource's identity. `/sensors/CO2-001` means "the sensor with ID CO2-001" — a unique, identifiable entity. Using a path segment for filtering, as in `/sensors/type/CO2`, incorrectly implies that `CO2` is a named sub-resource of `sensors`, a distinct addressable entity — which it is not. A filter criterion is not a resource; it is a search modifier applied to the collection.

**Query parameters** are semantically correct for filtering because they represent optional search criteria applied to a collection without changing the identity of the collection itself. `/api/v1/sensors` is always the sensors collection, regardless of what query parameters are appended. This has several concrete advantages:

1. **Composability:** Multiple filters combine naturally: `?type=CO2&status=ACTIVE&roomId=LIB-301`. The path-based approach requires either deeply nested routes (`/sensors/type/CO2/status/ACTIVE`) or explosion of separate endpoint definitions — neither of which is maintainable.

2. **Optional filtering with a single endpoint:** When `@QueryParam("type")` is absent from the request, Jersey injects `null`. A single `if (type != null)` check handles both the filtered and unfiltered cases. The path-based approach requires two entirely separate `@Path` and `@GET` annotated methods — one for `/sensors` and one for `/sensors/type/{value}`.

3. **Semantic correctness and cacheability:** HTTP caching proxies and CDNs treat URLs as resource identifiers. Query parameters are understood as modifiers on a collection — semantically aligned with HTTP specifications. Filtering as a path segment pollutes the resource identity namespace and can produce incorrect caching behaviour.

4. **REST convention alignment:** The de facto REST convention, followed by major public APIs (GitHub, Twitter, Google), uses query parameters for filtering, searching, and sorting collections. Consistency with this convention reduces the learning curve for API consumers.

---

### Part 4.1 — Sub-Resource Locator Pattern Benefits

**Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

**Answer:**

The Sub-Resource Locator pattern uses a JAX-RS method **without any HTTP method annotation** (`@GET`, `@POST`, etc.) to return an instance of another resource class. JAX-RS delegates all further request processing for that path prefix to the returned instance, effectively handing off control to a specialised handler. The key architectural benefits are:

**1. Single Responsibility Principle:** `SensorResource` is responsible for sensor-level operations — listing sensors, registering a new sensor, validating room references. `SensorReadingResource` is exclusively responsible for reading-level operations — retrieving history, appending new readings, enforcing the maintenance constraint, updating the parent sensor's `currentValue`. Each class has one well-defined purpose. Violating this by adding all reading logic to `SensorResource` would cause the class to violate the Single Responsibility Principle and grow unboundedly as the API evolves.

**2. Context injection via constructor:** The locator method passes `sensorId` directly into the `SensorReadingResource` constructor: `return new SensorReadingResource(sensorId)`. The sub-resource receives its operational context as a clean constructor argument — it never needs to re-parse path parameters or query the DataStore for the sensor ID. This is cleaner and more testable than passing context through thread-local state or static variables.

**3. Scalability of the codebase:** In a large API with dozens of resource types (Rooms, Sensors, Buildings, Floors, Equipment, Maintenance Tickets), each with multiple sub-resource levels, placing every `@Path` handler in one controller quickly results in a class with hundreds of methods and thousands of lines. The locator pattern allows independent development — different developers can work on `SensorReadingResource` and `SensorResource` simultaneously without merge conflicts. File sizes stay manageable and cohesion stays high.

**4. Independent testability:** Each resource class can be unit tested in complete isolation. `new SensorReadingResource("CO2-001")` can be instantiated directly in a unit test without spinning up a full Jersey server or mocking the entire JAX-RS pipeline. This dramatically reduces the cost of testing complex nested logic.

---

### Part 5.1 — HTTP 422 vs HTTP 404 for Missing References

**Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

**Answer:**

HTTP 404 Not Found is defined as: "the server cannot find the resource identified by the **Request-URI**." If a client sends `POST /api/v1/sensors` and that endpoint is valid and handled by the server, the URI is found — returning 404 is semantically incorrect because it falsely implies the endpoint does not exist.

HTTP 422 Unprocessable Entity is defined (RFC 9110) as appropriate when "the server understands the content type of the request content and the syntax of the request content is correct, but it was unable to process the contained instructions." This maps precisely to the scenario: the request body is syntactically valid JSON, the `Content-Type` is correct, the endpoint exists, but the body contains a reference (`"roomId": "ROOM-999"`) to an entity that does not exist in the system. The semantic problem lies **inside the payload**, not in the URL.

The distinction has real consequences for API consumers:

- A `404` on a POST request naturally leads a developer to question whether they are calling the right URL, causing them to check documentation, try different endpoint paths, or re-read the API guide — none of which will solve a `roomId` problem.
- A `422` clearly signals: "We received your request, understood it, but cannot fulfil it because of a semantic problem in your data." The client knows immediately to verify and correct the `roomId` field value, not the URL structure.

This actionability difference reduces debugging time and support burden. Using 422 (rather than the more generic 400 Bad Request) further narrows the error category from "your request format was wrong" to "your request was correctly formatted but semantically invalid" — giving API consumers the most precise possible signal about what needs to be fixed.

---

### Part 5.2 — Security Risks of Exposing Stack Traces

**Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

**Answer:**

Exposing Java stack traces to external consumers is a significant security vulnerability classified under **OWASP Top 10 A05:2021 — Security Misconfiguration** and specifically as an **Information Disclosure** vulnerability. An attacker who receives a raw stack trace can extract multiple categories of intelligence:

**1. Internal file system paths and package structure:** Stack frames include fully-qualified class names and source file paths (e.g., `com.smartcampus.resource.SensorResource.createSensor(SensorResource.java:67)`). This reveals the server's directory layout, package naming conventions, and deployment structure — reducing the reconnaissance effort required for targeted attacks.

**2. Library names and exact versions:** Every library in the call chain appears in the stack trace with its fully-qualified class name (e.g., `org.glassfish.jersey.server.ContainerRequest`, `com.fasterxml.jackson.databind.ObjectMapper`, `org.glassfish.grizzly.http.server.HttpHandler`). An attacker cross-references these against CVE databases (National Vulnerability Database, Mitre) to identify known vulnerabilities in the exact library versions in use, enabling targeted exploit selection.

**3. Application logic and validation failures:** Method names and line numbers reveal the business logic flow — which methods perform validation, where validation fails, which code paths reach the data layer. This allows an attacker to understand the application's internal architecture and identify which inputs bypass validation: information that would otherwise require weeks of black-box fuzzing.

**4. Infrastructure and database details:** Stack traces originating from persistence or network layers expose JDBC driver classes, connection pool implementations, internal hostnames, and sometimes fragments of SQL queries — a treasure map for database-layer attacks.

The mitigation used in this implementation — `GlobalExceptionMapper<Throwable>` — logs the full stack trace internally via `java.util.logging.Logger` (accessible only to authorised personnel with server access) while returning a generic, uninformative `500 Internal Server Error` JSON response externally. This preserves full debuggability for developers while providing zero intelligence to external consumers.

---

*Smart Campus API — 5COSC022W Client-Server Architectures Coursework 2025/26*  
*University of Westminster*
