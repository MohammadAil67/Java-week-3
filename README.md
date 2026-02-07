# Java HTTPS Server for Orbital Data

This project implements a complete HTTPS server for managing orbital observation data using Java. It fulfills all requirements from Week 3 exercises (Exercises 1-3).

## Features

### Exercise 1 - Basic HTTP Server
- ✅ GET and POST request handling
- ✅ Message storage and retrieval
- ✅ Error handling for unsupported methods (DELETE, etc.)

### Exercise 2 - HTTPS and Authentication
- ✅ HTTPS server with SSL/TLS encryption
- ✅ Self-signed certificate support
- ✅ Basic HTTP authentication
- ✅ User registration endpoint (unauthenticated)
- ✅ Protected data endpoint (requires authentication)

### Exercise 3 - JSON Data Structures
- ✅ JSON-based user registration (username, password, email)
- ✅ JSON-based orbital data messages
- ✅ Support for orbital_elements and/or state_vector
- ✅ JSON array responses for message retrieval
- ✅ Proper validation and error handling

## Building the Project

```bash
mvn clean compile
```

## Running the Server

```bash
mvn exec:java -Dexec.mainClass="com.server.Server" -Dexec.args="<keystore-path> <keystore-password>"
```

Example:
```bash
mvn exec:java -Dexec.mainClass="com.server.Server" -Dexec.args="keystore.jks password"
```

## Creating the Keystore

Generate a self-signed certificate using keytool:

```bash
keytool -genkey -alias alias -keyalg RSA -keystore keystore.jks -keysize 2048 -dname "CN=localhost, OU=Test, O=Test, L=Test, ST=Test, C=US" -storepass password -keypass password
```

## API Endpoints

### 1. User Registration (No Authentication Required)

**POST** `/registration`

Register a new user with JSON data:

```bash
curl -k -d '{"username":"testuser","password":"testpass","email":"test@example.com"}' \
  https://localhost:8001/registration \
  -H 'Content-Type: application/json'
```

**Response:**
- `200 OK` - User registered successfully
- `400 Bad Request` - Invalid JSON or missing fields
- `409 Conflict` - User already registered

### 2. Post Orbital Data (Authentication Required)

**POST** `/datarecord`

Send orbital observation data:

```bash
# With orbital_elements only
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Moon 301","center_body_name":"Earth 399","epoch":"2025-01-01T00:00:00Z","orbital_elements":{"semi_major_axis_au":1.458,"eccentricity":0.223,"inclination_deg":10.829,"longitude_ascending_node_deg":304.3,"argument_of_periapsis_deg":178.7,"mean_anomaly_deg":120.5}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'

# With state_vector only
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Asteroid 42","center_body_name":"Sun 10","epoch":"2025-02-01T00:00:00Z","state_vector":{"position_au":[0.123,1.456,-0.789],"velocity_au_per_day":[-0.012,0.015,0.001]}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'

# With both orbital_elements and state_vector
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Comet X","center_body_name":"Sun 10","epoch":"2025-03-01T00:00:00Z","orbital_elements":{"semi_major_axis_au":2.5,"eccentricity":0.5,"inclination_deg":20.0,"longitude_ascending_node_deg":100.0,"argument_of_periapsis_deg":50.0,"mean_anomaly_deg":80.0},"state_vector":{"position_au":[1.0,2.0,3.0],"velocity_au_per_day":[0.01,0.02,0.03]}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'
```

**Response:**
- `200 OK` - Message stored successfully
- `400 Bad Request` - Invalid JSON, missing required fields, or both orbital_elements and state_vector are missing
- `401 Unauthorized` - Authentication required

**Note:** Messages MUST contain at least one of `orbital_elements` or `state_vector` (or both), otherwise the server will reject with 400 error.

### 3. Get Orbital Data (Authentication Required)

**GET** `/datarecord`

Retrieve all stored orbital observations:

```bash
curl -k -u testuser:testpass \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'
```

**Response:**
- `200 OK` - Returns JSON array of all messages
- `204 No Content` - No messages stored
- `401 Unauthorized` - Authentication required

Example response:
```json
[
  {
    "target_body_name": "Moon 301",
    "center_body_name": "Earth 399",
    "epoch": "2025-01-01T00:00:00Z",
    "orbital_elements": {
      "semi_major_axis_au": 1.458,
      "eccentricity": 0.223,
      "inclination_deg": 10.829,
      "longitude_ascending_node_deg": 304.3,
      "argument_of_periapsis_deg": 178.7,
      "mean_anomaly_deg": 120.5
    }
  }
]
```

### 4. Unsupported Methods

**DELETE/PUT/PATCH** `/datarecord`

```bash
curl -k -u testuser:testpass -X DELETE https://localhost:8001/datarecord
```

**Response:**
- `400 Bad Request` - "Not supported"

## Default User

A default user is created for testing:
- Username: `dummy`
- Password: `passwd`

## Security Features

- HTTPS/TLS encryption
- Basic HTTP authentication
- Thread-safe data structures (ConcurrentHashMap, CopyOnWriteArrayList)
- Input validation for empty fields
- Proper HTTP status codes
- UTF-8 encoding throughout

## Project Structure

```
.
├── pom.xml                           # Maven configuration
├── keystore.jks                      # SSL certificate (generated)
└── src/main/java/com/server/
    ├── Server.java                   # Main server class with HTTPS and message handling
    ├── UserAuthenticator.java        # Basic authentication implementation
    ├── RegistrationHandler.java      # User registration endpoint
    ├── User.java                     # User data model
    └── ObservationRecord.java        # Orbital data model
```

## Dependencies

- Java 11+
- Maven 3.6+
- org.json:json:20250107

## Testing

All functionality has been tested with curl:
- ✅ User registration (success and duplicate)
- ✅ Posting messages with orbital_elements only
- ✅ Posting messages with state_vector only
- ✅ Posting messages with both
- ✅ Rejecting messages without either
- ✅ Retrieving messages as JSON array
- ✅ Authentication enforcement
- ✅ Unsupported method handling
- ✅ Empty field validation

## Security Summary

✅ No security vulnerabilities detected by CodeQL analysis.

**Note:** For coursework purposes, passwords are stored in plaintext as per the exercise requirements. In production, use proper password hashing (BCrypt, PBKDF2, or Argon2).
