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

### Part 1.1 - JAX-RS Resource Lifecycle & Thread Safety

**Question:** In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance created for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronise your in-memory data structures (maps/lists) to prevent data loss or race conditions.

**Answer:**
By default, JAX-RS resource classes are **request-scoped**, meaning the runtime instantiates a brand-new instance for every incoming HTTP request and discards it afterward, rather than using a singleton. This architecture requires shared state to be stored in static fields (like our `DataStore` maps) so data persists across requests. Because multiple threads will access these static fields concurrently when handling simultaneous HTTP requests, we use a `ConcurrentHashMap` to safely manage reads and writes without data corruption or `ConcurrentModificationException`s, and atomic methods like `computeIfAbsent` to prevent race conditions during list initialisation.

---

### Part 1.2 - HATEOAS and Hypermedia in RESTful APIs

**Question:** Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

**Answer:**
HATEOAS (Hypermedia as the Engine of Application State) is the principle that API responses should embed navigational links to related resources, allowing clients to discover endpoints dynamically (Level 3 of the Richardson Maturity Model). Unlike static documentation, where clients hardcode URLs and break when paths change, HATEOAS drastically reduces coupling; a client can query a single stable entry point (like our `GET /api/v1`) and use the returned links to navigate to rooms and sensors. If the server's routing changes in the future, the updated links are automatically provided in the response, allowing clients to adapt seamlessly without requiring manual code updates.

---

### Part 2.1 - Returning IDs vs Full Objects in List Responses

**Question:** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing.

**Answer:**
Returning **full room objects** in a list response is highly efficient for clients that need immediate access to all data (like a dashboard) because it requires only a single HTTP round trip, avoiding the "N+1 query problem" where returning **only IDs** would force the client to make hundreds of subsequent requests to fetch individual details. While returning only IDs saves initial payload bandwidth, the massive latency and server overhead incurred by those follow-up requests make it impractical for most operations. For this coursework, returning full objects is the optimal and required approach for moderate-sized collections, while enterprise APIs might supplement this with pagination or field projection for enormous datasets.

---

### Part 2.2 - Idempotency of DELETE

**Question:** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

**Answer:**
Yes, the DELETE operation in this implementation is strictly **idempotent** per the HTTP specification (RFC 7231). Idempotency guarantees that issuing the exact same DELETE request multiple times leaves the server in the exact same state as if it were issued once: the first request removes the room and returns `204 No Content`, while all subsequent duplicate requests find that the room is already gone and return `404 Not Found`. Despite the different response codes, the underlying server state—that the specified room does not exist—remains identical after every call, successfully fulfilling the idempotency requirement.

---

### Part 3.1 - `@Consumes` and Media Type Mismatches

**Question:** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

**Answer:**
The `@Consumes(MediaType.APPLICATION_JSON)` annotation registers a pre-dispatch content negotiation constraint, meaning the JAX-RS runtime inspects the `Content-Type` header of an incoming request before routing it to our application code. If a client attempts to send data in an unsupported format like `text/plain`, the Jersey message body reader selection algorithm fails to find a matching deserialiser and automatically rejects the request with an **HTTP 415 Unsupported Media Type** error. This provides a robust, framework-level enforcement mechanism that protects the API from malformed payloads before a single line of our custom business logic ever executes.

---

### Part 3.2 - `@QueryParam` vs Path-Based Filtering

**Question:** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

**Answer:**
Using `@QueryParam` (e.g., `/sensors?type=CO2`) is semantically superior for filtering because query parameters act as optional search modifiers applied to a collection without changing the core identity of the resource. Conversely, a path-based approach (e.g., `/sensors/type/CO2`) incorrectly implies that "CO2" is a uniquely addressable sub-resource rather than a filter criterion. Architecturally, query parameters allow for natural composability of multiple filters and enable a single JAX-RS endpoint to cleanly handle both filtered and unfiltered requests via simple null checks, avoiding the unmaintainable endpoint explosion required by nested path routes.

---

### Part 4.1 - Sub-Resource Locator Pattern Benefits

**Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., `sensors/{id}/readings/{rid}`) in one massive controller class?

**Answer:**
The Sub-Resource Locator pattern (a method annotated with `@Path` but no HTTP verb) provides massive architectural benefits by delegating the processing of nested routes (like `/sensors/{id}/readings`) to a completely separate `SensorReadingResource` class. This enforces the Single Responsibility Principle, ensuring `SensorResource` only handles sensor-level logic rather than bloating into a massive, unmaintainable controller. Furthermore, because the locator method injects the parent `sensorId` directly into the sub-resource's constructor, it elegantly passes contextual state, allowing multiple developers to work independently on different layers of the API hierarchy without merge conflicts or duplicated validation logic.

---

### Part 5.2 - HTTP 422 vs HTTP 404 for Missing References

**Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

**Answer:**
Returning HTTP 422 (Unprocessable Entity) is semantically accurate for a missing reference (e.g., an invalid `roomId` inside the JSON payload) because it explicitly informs the client that the server understood the request and the JSON syntax was valid, but the contained instructions could not be processed due to a semantic data error. In contrast, returning HTTP 404 (Not Found) is highly misleading because 404 strictly indicates that the requested URL endpoint itself does not exist. A 422 response immediately guides the developer to fix their payload data, whereas a 404 would cause them to incorrectly suspect a routing issue or an offline server.

---

### Part 5.4 - Security Risks of Exposing Stack Traces

**Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

**Answer:**
Exposing internal Java stack traces to API consumers is a critical Information Disclosure vulnerability because it hands attackers a precise blueprint of the server's backend. Stack traces reveal internal directory structures, exact package naming conventions, validation logic flows, and most dangerously, the exact versions of underlying libraries, which attackers can cross-reference against CVE databases to launch highly targeted exploits. Our implementation mitigates this entirely via the `GlobalExceptionMapper<Throwable>`, which intercepts all unhandled crashes, logs the full technical stack trace securely on the Tomcat server via `java.util.logging.Logger`, and returns only a generic, leak-proof `500 Internal Server Error` JSON response to the external client.

---

### Part 5.5 - API Request & Response Logging Filters

**Question:** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every single resource method?

**Answer:**
Using JAX-RS container filters (`ContainerRequestFilter` and `ContainerResponseFilter`) provides a centralised, aspect-oriented approach to cross-cutting concerns, ensuring that logging logic is completely decoupled from core business logic. If developers manually insert `Logger.info()` into every resource method, it clutters the code, risks being forgotten in new endpoints, and makes formatting changes incredibly tedious because every method must be updated. By implementing logging at the filter level, the framework automatically intercepts every single incoming request and outgoing response—including those that fail before reaching a resource method (like a 415 or 404)—guaranteeing 100% observability across the entire API with zero duplicated code.
