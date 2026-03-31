# TicketSync

A cross-platform desktop ticket management system built with JavaFX 21 and PostgreSQL.

## Prerequisites

- Java 21 LTS (Eclipse Adoptium or equivalent)
- Apache Maven 3.9+
- PostgreSQL 15+

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

## Development

This project uses:
- **MVVM Pattern:** Model-View-ViewModel architectural pattern
- **FXML:** Declarative UI with FXML + Controllers
- **Modular Java:** Java Platform Module System for strong encapsulation