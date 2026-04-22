# Smart Campus Sensor & Room Management API

> **Module:** 5COSC022W – Client-Server Architectures | University of Westminster  
> **Technology:** Java EE 8 · JAX-RS (Jersey 2.39) · Apache Tomcat 9 · Jackson JSON  
> **Base URL:** `http://localhost:8080/api/v1`

---

## 1. Overview

This project is a fully RESTful Web Application (`.war`) built using **JAX-RS (Jersey 2.39)** and engineered for deployment on **Apache Tomcat 9**. The API provides a comprehensive interface for campus facilities managers and automated building systems to manage **Rooms** and **IoT Sensors** (CO2 monitors, occupancy trackers, smart lighting controllers) across campus.

### Key Design Principles
- **RESTful architecture** with proper HTTP verbs, status codes, and HATEOAS discovery.
- **Java EE 8 Compatibility** using the `javax.*` namespace for seamless Tomcat 9 integration.
- **In-memory data storage** using `ConcurrentHashMap` for thread-safe access without a database.
- **Sub-resource delegation** reflecting physical nested structures (`/sensors/{id}/readings`).
- **Zero stack trace leakage** — all errors return structured JSON bodies via custom `ExceptionMapper`s.

---

## 2. How to Run (3 Options)

This API is packaged as a standard Java Web Application (`ROOT.war`). You can run it using any of the following three methods:

### Option 1: Terminal (Quickest)
Run the application instantly from your terminal using the embedded Jetty plugin without needing a standalone Tomcat installation.
```bash
mvn clean jetty:run
```
*The server will start immediately at `http://localhost:8080/api/v1`. Press `Ctrl + C` to stop.*

### Option 2: NetBeans IDE
Run the project directly inside NetBeans using an attached Apache Tomcat 9 server.
1. In NetBeans, go to **Services > Servers** and add your Apache Tomcat 9 installation.
2. Right-click the `smart-campus-api` project and select **Properties**.
3. Under the **Run** category, select "Apache Tomcat" as the Server and set the Context Path to exactly `/`.
4. Click the **Run** (Play) button.

### Option 3: Manual Tomcat Deployment (Production Style)
Build the `.war` archive and deploy it manually to a standalone Tomcat server.
1. Build the project:
   ```bash
   mvn clean package
   ```
2. Navigate to the `target/` directory and copy `ROOT.war`.
3. Paste `ROOT.war` into your Tomcat `webapps/` folder. *(Ensure any old `ROOT` folder is deleted first).*
4. Run Tomcat (`bin/startup.bat`).
5. The API will be available at `http://localhost:8080/api/v1`.

---

## 3. Sample cURL Commands (Top 5)

### 1. API Discovery (HATEOAS)
```bash
curl -X GET http://localhost:8080/api/v1/
```

### 2. Create a Room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LIB-301","name":"Library Quiet Study","capacity":50}'
```

### 3. Register a New Sensor
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-001","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}'
```

### 4. Add a Sensor Reading (Sub-Resource)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":412.5}'
```

### 5. Trigger a Global 500 Error (Secure logging demonstration)
```bash
curl -X GET http://localhost:8080/api/v1/crash
```

---

## 4. Report: Question Answers

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

---

### Part 3.1 — `@Consumes` and Media Type Mismatches

**Question:** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

**Answer:**
The `@Consumes(MediaType.APPLICATION_JSON)` annotation registers a **pre-dispatch content negotiation constraint** with the JAX-RS runtime. When a request arrives, the runtime inspects the `Content-Type` request header before selecting a method to invoke. If the header value does not match any of the media types declared in `@Consumes` for the available methods, the runtime cannot find a matching handler and automatically generates an **HTTP 415 Unsupported Media Type** response — before a single line of application code executes.

This is a framework-level enforcement mechanism, not application logic. The error is generated by the Jersey message body reader selection algorithm, which walks the list of registered `MessageBodyReader` implementations and finds no reader capable of deserialising the incoming `text/plain` or `application/xml` content into the target Java type (`Room`, `Sensor`, etc.). The JAX-RS specification mandates that 415 is returned in this situation.

---

### Part 3.2 — `@QueryParam` vs Path-Based Filtering

**Question:** You implemented filtering using `@QueryParam`. Contrast this with an alternative design using path parameters (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

**Answer:**
The distinction is semantic and architectural. **Path parameters** identify a specific, named resource within a hierarchy — they are part of the resource's identity. `/sensors/CO2-001` means "the sensor with ID CO2-001" — a unique, identifiable entity. Using a path segment for filtering, as in `/sensors/type/CO2`, incorrectly implies that `CO2` is a named sub-resource of `sensors`, a distinct addressable entity — which it is not. A filter criterion is not a resource; it is a search modifier applied to the collection.

**Query parameters** are semantically correct for filtering because they represent optional search criteria applied to a collection without changing the identity of the collection itself. `/api/v1/sensors` is always the sensors collection, regardless of what query parameters are appended. This has several concrete advantages:

1. **Composability:** Multiple filters combine naturally. The path-based approach requires either deeply nested routes or an explosion of separate endpoint definitions — neither of which is maintainable.
2. **Optional filtering with a single endpoint:** When `@QueryParam("type")` is absent, Jersey injects `null`. A single `if (type != null)` check handles both cases. The path-based approach requires two entirely separate `@Path` and `@GET` annotated methods.
3. **Semantic correctness:** HTTP caching proxies treat query parameters as modifiers on a collection — semantically aligned with HTTP specifications.

---

### Part 4.1 — Sub-Resource Locator Pattern Benefits

**Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

**Answer:**
The Sub-Resource Locator pattern uses a JAX-RS method **without any HTTP method annotation** (`@GET`, `@POST`, etc.) to return an instance of another resource class. JAX-RS delegates all further request processing for that path prefix to the returned instance, effectively handing off control. The key architectural benefits are:

**1. Single Responsibility Principle:** `SensorResource` is responsible for sensor-level operations. `SensorReadingResource` is exclusively responsible for reading-level operations. Each class has one well-defined purpose. Violating this by adding all reading logic to `SensorResource` would cause the class to grow unboundedly as the API evolves.

**2. Context injection via constructor:** The locator method passes `sensorId` directly into the `SensorReadingResource` constructor: `return new SensorReadingResource(sensorId)`. The sub-resource receives its operational context as a clean constructor argument — it never needs to re-parse path parameters.

**3. Scalability of the codebase:** In a large API with dozens of resource types, placing every `@Path` handler in one controller quickly results in thousands of lines of code. The locator pattern allows independent development — different developers can work on `SensorReadingResource` and `SensorResource` simultaneously without merge conflicts.

---

### Part 5.1 — HTTP 422 vs HTTP 404 for Missing References

**Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

**Answer:**
HTTP 404 Not Found is defined as: "the server cannot find the resource identified by the **Request-URI**." If a client sends `POST /api/v1/sensors` and that endpoint is valid and handled by the server, the URI is found. Returning 404 is semantically incorrect because it falsely implies the endpoint does not exist.

HTTP 422 Unprocessable Entity is appropriate when "the server understands the content type of the request content and the syntax of the request content is correct, but it was unable to process the contained instructions." This maps precisely to the scenario: the request body is syntactically valid JSON, the `Content-Type` is correct, the endpoint exists, but the body contains a reference (`"roomId": "ROOM-999"`) to an entity that does not exist in the system. The semantic problem lies **inside the payload**, not in the URL.

A `404` on a POST request leads developers to question whether they are calling the right URL. A `422` clearly signals: "We received your request, understood it, but cannot fulfil it because of a semantic problem in your data." This directly tells the client to verify the `roomId` field value.

---

### Part 5.2 — Security Risks of Exposing Stack Traces

**Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

**Answer:**
Exposing Java stack traces to external consumers is a significant security vulnerability classified under **OWASP Top 10 A05:2021 — Security Misconfiguration** and specifically as an **Information Disclosure** vulnerability. An attacker who receives a raw stack trace can extract multiple categories of intelligence:

**1. Internal file system paths and package structure:** Stack frames include fully-qualified class names and source file paths. This reveals the server's directory layout, package naming conventions, and deployment structure — reducing the reconnaissance effort required for targeted attacks.

**2. Library names and exact versions:** Every library in the call chain appears in the stack trace. An attacker cross-references these against CVE databases to identify known vulnerabilities in the exact library versions in use, enabling targeted exploit selection.

**3. Application logic and validation failures:** Method names and line numbers reveal the business logic flow — which methods perform validation, where validation fails, which code paths reach the data layer.

The mitigation used in this implementation — `GlobalExceptionMapper<Throwable>` — logs the full stack trace internally via `java.util.logging.Logger` while returning a generic, uninformative `500 Internal Server Error` JSON response externally. This preserves full debuggability for developers while providing zero intelligence to external consumers.
