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