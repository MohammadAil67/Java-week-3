# Java HTTPS Server for Orbital Data

This project implements a complete HTTPS server for managing orbital observation data using Java. It fulfills all requirements from Week 3 including password hashing, observatory support, and proper API specifications.

## Features

### Exercise 1 - Basic HTTP Server
- ✅ GET and POST request handling
- ✅ Message storage and retrieval
- ✅ Error handling for unsupported methods (DELETE, etc.)

### Exercise 2 - HTTPS and Authentication
- ✅ HTTPS server with SSL/TLS encryption
- ✅ Self-signed certificate support
- ✅ Basic HTTP authentication with BCrypt password hashing
- ✅ User registration endpoint (unauthenticated)
- ✅ Protected data endpoint (requires authentication)

### Exercise 3 - JSON Data Structures
- ✅ JSON-based user registration (username, password, email, nickname)
- ✅ JSON-based orbital data messages
- ✅ Support for orbital_elements and/or state_vector
- ✅ JSON array responses for message retrieval
- ✅ Proper validation and error handling
- ✅ Mandatory record_payload field in observations
- ✅ Observatory information support (latitude, longitude, observatory_name)

## API Requirements Met

### REQ1-5: HTTP/JSON Formatting
- ✅ REQ1: All text in HTTP body is UTF-8
- ✅ REQ2: All content in requests/responses is JSON
- ✅ REQ3: All dates/times follow ISO 8601 format in UTC with milliseconds
- ✅ REQ4: HTTP headers contain content size and content type
- ✅ REQ5: Content-Type is "application/json"

### REQ6: Database Storage
- ✅ SQLite database for user and observation data
- ✅ Relational tables for users, messages, and observatories

## Building the Project

```bash
mvn clean compile
```

## Running the Server

```bash
export DATABASE_PATH=<path-to-database>
java -cp target/observation-server-1.0-SNAPSHOT.jar com.o3.server.Server <keystore-path> <keystore-password>
```

Example:
```bash
export DATABASE_PATH=observation.db
java -cp target/observation-server-1.0-SNAPSHOT.jar com.o3.server.Server keystore.jks password
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
curl -k -d '{"username":"testuser","password":"testpass","email":"test@example.com","nickname":"TestNick"}' \
  https://localhost:8001/registration \
  -H 'Content-Type: application/json'
```

**Response:**
- `201 Created` - User registered successfully
- `400 Bad Request` - Invalid JSON or missing fields
- `409 Conflict` - User already registered

**Note:** Passwords are hashed using BCrypt before storage for security.

### 2. Post Orbital Data (Authentication Required)

**POST** `/datarecord`

Send orbital observation data. **record_payload is mandatory** in the metadata section:

```bash
# With orbital_elements only
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Moon 301","center_body_name":"Earth 399","epoch":"2025-01-01T00:00:00Z","orbital_elements":{"semi_major_axis_au":1.458,"eccentricity":0.223,"inclination_deg":10.829,"longitude_ascending_node_deg":304.3,"argument_of_periapsis_deg":178.7,"mean_anomaly_deg":120.5},"metadata":{"record_payload":"Example payload"}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'

# With state_vector only
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Asteroid 42","center_body_name":"Sun 10","epoch":"2025-02-01T00:00:00Z","state_vector":{"position_au":[0.123,1.456,-0.789],"velocity_au_per_day":[-0.012,0.015,0.001]},"metadata":{"record_payload":"Asteroid observation"}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'

# With both orbital_elements and state_vector
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Comet X","center_body_name":"Sun 10","epoch":"2025-03-01T00:00:00Z","orbital_elements":{"semi_major_axis_au":2.5,"eccentricity":0.5,"inclination_deg":20.0,"longitude_ascending_node_deg":100.0,"argument_of_periapsis_deg":50.0,"mean_anomaly_deg":80.0},"state_vector":{"position_au":[1.0,2.0,3.0],"velocity_au_per_day":[0.01,0.02,0.03]},"metadata":{"record_payload":"Comet observation"}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'

# With observatory information
curl -k -u testuser:testpass \
  -d '{"target_body_name":"Mars","center_body_name":"Sun","epoch":"2025-04-01T00:00:00Z","orbital_elements":{"semi_major_axis_au":1.524,"eccentricity":0.093,"inclination_deg":1.85,"longitude_ascending_node_deg":49.5,"argument_of_periapsis_deg":286.5,"mean_anomaly_deg":19.4},"metadata":{"record_payload":"Mars from observatory","observatory":[{"latitude":43.10591835328922,"observatory_name":"Example observatory","longitude":50.85719242538301}]}}' \
  https://localhost:8001/datarecord \
  -H 'Content-Type: application/json'
```
```

**Response:**
- `201 Created` - Message stored successfully
- `400 Bad Request` - Invalid JSON, missing required fields, invalid data types, or both orbital_elements and state_vector are missing
- `401 Unauthorized` - Authentication required

**Data Type Requirements:**
- All fields in `orbital_elements` must be numeric values (not strings)
- `position_au` and `velocity_au_per_day` in `state_vector` must be arrays of numeric values
- The server validates data types and returns 400 for any type mismatches
- **record_payload is mandatory** in the metadata section

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
    },
    "metadata": {
      "record_time_received": "2026-02-08T21:56:03.195Z",
      "record_owner": "TestNick",
      "id": 1,
      "record_payload": "Example payload"
    }
  },
  {
    "target_body_name": "Mars",
    "center_body_name": "Sun",
    "epoch": "2025-02-01T00:00:00Z",
    "state_vector": {
      "position_au": [0.123, 1.456, -0.789],
      "velocity_au_per_day": [-0.012, 0.015, 0.001]
    },
    "metadata": {
      "record_time_received": "2026-02-08T21:56:10.269Z",
      "record_owner": "TestNick",
      "id": 2,
      "record_payload": "Mars observation",
      "observatory": [
        {
          "latitude": 43.10591835328922,
          "observatory_name": "Example observatory",
          "longitude": 50.85719242538301
        }
      ]
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
- Basic HTTP authentication with BCrypt password hashing
- Passwords are hashed and salted using BCrypt before storage
- Database storage for users and observations
- Input validation for empty fields and data types
- Proper HTTP status codes
- UTF-8 encoding throughout

## Project Structure

```
.
├── pom.xml                              # Maven configuration
├── keystore.jks                         # SSL certificate (generated)
└── src/main/java/com/o3/server/
    ├── Server.java                      # Main server class with HTTPS and message handling
    ├── UserAuthenticator.java           # Basic authentication with BCrypt
    ├── RegistrationHandler.java         # User registration endpoint
    ├── MessageDatabase.java             # SQLite database operations
    ├── User.java                        # User data model
    ├── ObservationRecord.java           # Orbital data model
    └── Observatory.java                 # Observatory data model
```

## Dependencies

- Java 11+
- Maven 3.6+
- org.json:json:20250107
- org.xerial:sqlite-jdbc:3.48.0.0
- org.mindrot:jbcrypt:0.4

## Testing

All functionality has been tested with curl:
- ✅ User registration (success and duplicate) with 201 status code
- ✅ BCrypt password hashing and authentication
- ✅ Posting messages with orbital_elements only
- ✅ Posting messages with state_vector only
- ✅ Posting messages with both orbital_elements and state_vector
- ✅ Posting messages with observatory information
- ✅ Mandatory record_payload validation
- ✅ Rejecting messages without orbital_elements or state_vector
- ✅ Rejecting messages with invalid data types (strings instead of numbers)
- ✅ Retrieving messages as JSON array with all metadata
- ✅ Authentication enforcement (401 for wrong credentials)
- ✅ Unsupported method handling
- ✅ Empty field validation
- ✅ Content-Type headers in all responses

## Security Summary

✅ No security vulnerabilities detected by CodeQL analysis.

**Password Security:** User passwords are hashed and salted using BCrypt (cost factor 10) before being stored in the database. BCrypt is a modern, secure password hashing algorithm designed to be slow and resistant to brute-force attacks.
