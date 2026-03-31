# TicketSync

A cross-platform desktop ticket management system built with JavaFX 21 and PostgreSQL.

## Prerequisites

- Java 21 LTS (Eclipse Adoptium or equivalent)
- Apache Maven 3.9+
- PostgreSQL 15+ (or Docker Desktop for containerized database)

## Database Setup

TicketSync requires PostgreSQL 15 or higher for data persistence. You can use either Docker (recommended) or a local PostgreSQL installation.

### Option 1: Docker Compose (Recommended)

The easiest way to run PostgreSQL for development:

```bash
# Start PostgreSQL container
docker-compose up -d

# Verify PostgreSQL is running
docker-compose ps

# Stop PostgreSQL
docker-compose down

# Stop and remove data volumes (WARNING: deletes all data)
docker-compose down -v
```

The Docker Compose configuration automatically:
- Creates the `ticketsync` database
- Sets up the `postgres` user with password `postgres`
- Exposes PostgreSQL on `localhost:5432`
- Persists data in a Docker volume

### Option 2: Local PostgreSQL Installation

If you prefer installing PostgreSQL directly:

**Windows:**
```powershell
# Using Chocolatey
choco install postgresql15

# Or download installer from postgresql.org
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get update
sudo apt-get install postgresql-15
sudo systemctl start postgresql
```

**macOS:**
```bash
# Using Homebrew
brew install postgresql@15
brew services start postgresql@15
```

**Create Database:**
```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE ticketsync;

# Exit psql
\q
```

### Apply Database Migrations

After setting up PostgreSQL, apply Flyway migrations to create the schema:

```bash
# Apply all pending migrations
mvn flyway:migrate

# View migration history
mvn flyway:info

# Validate applied migrations
mvn flyway:validate
```

**Expected Output:**
```
[INFO] Successfully applied 1 migration to schema "public", now at version v001
```

### Database Connection Settings

The application connects to PostgreSQL using:
- **URL:** `jdbc:postgresql://localhost:5432/ticketsync`
- **User:** `postgres`
- **Password:** `postgres`
- **Port:** `5432` (default)

**Note:** These credentials are for development only. Production credentials will be encrypted using Jasypt.

## Connection Pooling

TicketSync uses **HikariCP 5.1.0** for efficient database connection management. HikariCP is the industry-standard JDBC connection pool with superior performance and reliability.

### Pool Configuration

Each application instance (booth or admin client) maintains its own connection pool with the following configuration:

- **Maximum Pool Size:** 5 connections (per application instance)
- **Minimum Idle:** 2 connections (maintained warm for instant checkout)
- **Connection Timeout:** 10 seconds
- **Idle Timeout:** 5 minutes
- **Max Lifetime:** 30 minutes

**Why Connection Pooling?** Unlike typical single-user desktop apps, TicketSync requires concurrent database operations:
- **1 persistent connection** for PostgreSQL LISTEN/NOTIFY (real-time seat updates)
- **1-2 connections** for transaction processing
- **1-2 connections** for concurrent queries and health checks

With 10 booths running, the total PostgreSQL connection count is approximately 20-50 connections (well within PostgreSQL's default limit).

### Performance

- Connection checkout completes in **< 50ms** under normal load
- Pool automatically manages connection lifecycle and health checks
- Connections are validated using `SELECT 1` query before checkout

### Usage Pattern

```java
Connection conn = null;
try {
    conn = DatabaseConfig.getConnection();
    // Use connection for database operations
} catch (SQLException e) {
    // Handle error
} finally {
    if (conn != null) {
        conn.close(); // Returns connection to pool
    }
}
```

**Important:** Calling `Connection.close()` returns the connection to the pool rather than closing the underlying PostgreSQL connection.

## Build & Run

### Development Mode

```bash
# Build and run the application
mvn clean javafx:run
```

### Build Only

```bash
# Compile the project
mvn clean compile

# Package as JAR
mvn clean package
```

### Create Custom Runtime Image (jlink)

```bash
# Create optimized runtime with jlink
mvn clean javafx:jlink
```

## Project Structure

```
ticketsync/
├── pom.xml                          # Maven build configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java    # Java Platform Module System descriptor
│   │   │   └── com/ticketsync/     # Application source code
│   │   └── resources/
│   │       └── com/ticketsync/     # FXML views and styles
│   └── test/
│       └── java/                    # JUnit test files
```

## Technology Stack

- **Java:** 21 LTS
- **UI Framework:** JavaFX 21.0.2
- **Database:** PostgreSQL 15+ with JDBC 42.7.2
- **Connection Pool:** HikariCP 5.1.0
- **Migrations:** Flyway 10.8.1
- **Build Tool:** Maven 3.9.14
- **Testing:** JUnit 5.10.0
- **Module System:** Java Platform Module System (JPMS)

## Maven Goals

| Goal | Description |
|------|-------------|
| `mvn clean compile` | Compile Java sources |
| `mvn clean javafx:run` | Run the application in development mode |
| `mvn clean package` | Package application as JAR |
| `mvn clean javafx:jlink` | Create custom runtime image |
| `mvn test` | Run unit tests |
| `mvn flyway:migrate` | Apply pending database migrations |
| `mvn flyway:info` | Display migration history |
| `mvn flyway:validate` | Validate applied migrations |
| `mvn flyway:clean` | ⚠️ Drop all database objects (dev only) |

## Development

This project uses:
- **MVVM Pattern:** Model-View-ViewModel architectural pattern
- **FXML:** Declarative UI with FXML + Controllers
- **Modular Java:** Java Platform Module System for strong encapsulation