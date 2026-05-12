# TicketSync

Sistema de gestión de tickets de escritorio multiplataforma construido con JavaFX 21 y PostgreSQL. Permite la venta de entradas a través de múltiples taquillas físicas en red local, garantizando cero sobreventas mediante transacciones ACID.

---

## Tabla de contenidos

1. [Requisitos previos](#1-requisitos-previos)
2. [Configuración de la base de datos](#2-configuración-de-la-base-de-datos)
3. [Configuración de Maven (desarrollo)](#3-configuración-de-maven-desarrollo)
4. [Ejecutar en modo desarrollo](#4-ejecutar-en-modo-desarrollo)
5. [Usuarios por defecto](#5-usuarios-por-defecto)
6. [Empaquetado y distribución](#6-empaquetado-y-distribución)
7. [Estructura del proyecto](#7-estructura-del-proyecto)
8. [Preguntas frecuentes](#8-preguntas-frecuentes)

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Notas |
|---|---|---|
| Java (JDK) | 21 LTS | [Eclipse Adoptium](https://adoptium.net/) recomendado |
| Apache Maven | 3.9+ | Solo necesario para desarrollo y compilación |
| PostgreSQL | 15+ | O Docker Desktop para el contenedor de base de datos |
| Docker Desktop | Cualquier versión actual | Opcional, pero recomendado para desarrollo |

> **Nota:** Los usuarios finales que instalen el paquete distribuido (`dist/`) **no necesitan** Java ni Maven; la imagen jlink incluye el runtime completo.

---

## 2. Configuración de la base de datos

TicketSync requiere PostgreSQL 15 o superior. Tienes dos opciones:

### Opción A: Docker Compose (recomendada)

```bash
# Iniciar PostgreSQL en segundo plano
docker compose up -d

# Verificar que está corriendo
docker compose ps

# Detener sin borrar datos
docker compose down

# Detener y borrar todos los datos (¡irreversible!)
docker compose down -v
```

Docker Compose crea automáticamente:
- Base de datos `ticketsync`
- Usuario `postgres` con contraseña `postgres`
- Puerto `5432` expuesto en `localhost`
- Volumen persistente `postgres_data`

### Opción B: PostgreSQL local

**Windows (Chocolatey):**
```powershell
choco install postgresql15
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get install postgresql-15
sudo systemctl start postgresql
```

**macOS (Homebrew):**
```bash
brew install postgresql@15
brew services start postgresql@15
```

**Crear la base de datos:**
```bash
psql -U postgres -c "CREATE DATABASE ticketsync;"
```

### Migraciones de esquema

La aplicación aplica las migraciones Flyway **automáticamente al arrancar**. No es necesario ejecutar `mvn flyway:migrate` manualmente.

Si prefieres aplicar las migraciones desde Maven (entornos CI/CD):

```bash
mvn flyway:migrate   # Aplica todas las migraciones pendientes
mvn flyway:info      # Muestra el historial de migraciones
mvn flyway:validate  # Valida las migraciones aplicadas
```

---

## 3. Configuración de Maven (desarrollo)

Las credenciales de Flyway se configuran en `~/.m2/settings.xml` para no exponerlas en el repositorio:

```xml
<settings>
    <profiles>
        <profile>
            <id>ticketsync-local</id>
            <properties>
                <flyway.url>jdbc:postgresql://localhost:5432/ticketsync</flyway.url>
                <flyway.user>postgres</flyway.user>
                <flyway.password>postgres</flyway.password>
            </properties>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>ticketsync-local</activeProfile>
    </activeProfiles>
</settings>
```

Ruta del fichero:
- **Windows:** `C:\Users\<tu-usuario>\.m2\settings.xml`
- **Linux / macOS:** `~/.m2/settings.xml`

---

## 4. Ejecutar en modo desarrollo

```bash
# Arrancar la base de datos
docker compose up -d

# Compilar y ejecutar
mvn clean javafx:run
```

### Referencia de comandos Maven

| Comando | Descripción |
|---|---|
| `mvn clean compile` | Compilar fuentes Java |
| `mvn clean javafx:run` | Ejecutar en modo desarrollo |
| `mvn clean package` | Generar JAR |
| `mvn clean javafx:jlink` | Crear imagen de runtime con jlink |
| `mvn test` | Ejecutar tests unitarios |
| `mvn flyway:migrate` | Aplicar migraciones pendientes |
| `mvn flyway:info` | Ver historial de migraciones |
| `mvn flyway:validate` | Validar migraciones aplicadas |
| `mvn flyway:clean` | ⚠️ Eliminar todo el esquema (solo desarrollo) |

---

## 5. Usuarios por defecto

Estos usuarios se crean automáticamente al aplicar la primera migración. Son **exclusivos para desarrollo y pruebas**; cámbialos inmediatamente en cualquier entorno real.

| Usuario | Contraseña | Rol | Descripción |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Acceso completo: gestión de eventos, zonas, usuarios y auditoría |
| `vendor1` | `vendor123` | `VENDOR` | Acceso al punto de venta (POS) |

> ⚠️ **Seguridad:** Cambia ambas contraseñas tras el primer inicio de sesión en cualquier entorno no-desarrollo. El panel de administración permite gestionar usuarios desde la pestaña *Usuarios*.

---

## 6. Empaquetado y distribución

Hay dos modos de empaquetado:

| Modo | Comando | Java en destino | Resultado |
|---|---|---|---|
| Portable (JARs + launcher) | `package.ps1` | ✅ Requerido (21+) | `dist/lib/` + `.bat` / `.sh` |
| Imagen nativa (jpackage) | `package.ps1 -exe` | ❌ No requerido | `dist/TicketSync-1.0.zip` |

> ⚠️ **Por qué no se usa jlink:** varias dependencias (jBCrypt, Flyway) son *automatic modules* (JARs sin `module-info.class`) y jlink no puede incluirlos directamente.

### Opción A — Distribución portable (requiere Java 21 en el destino)

```powershell
# Windows
.\scripts\package.ps1
```

El resultado se generará en `dist/`:
```
dist/
├── lib/               ← todos los JARs (app + dependencias + JavaFX con libs nativas)
├── ticketsync.bat     ← launcher Windows
└── ticketsync.sh      ← launcher Linux / macOS
```

Para ejecutar:
```powershell
.\dist\ticketsync.bat
```

### Opción B — Imagen nativa autosuficiente con jpackage (recomendada)

Incorpora un JRE completo: **el usuario final no necesita Java instalado**.

```powershell
.\scripts\package.ps1 -exe
```

Genera:
```
dist/
├── app-image/TicketSync/   ← imagen nativa con JRE embebido (no versionar)
└── TicketSync-1.0.zip      ← ZIP listo para entregar ✅
```

Para ejecutar tras descomprimir el ZIP:
```powershell
.\TicketSync\TicketSync.exe
```

### Pasos completos para distribuir

```
1. docker compose up -d                          ← Arrancar PostgreSQL (solo build)
2. .\scripts\package.ps1 -exe              ← Compilar + generar ZIP
3. Entregar dist\TicketSync-1.0.zip al usuario final
   → Descomprimir
   → Ejecutar TicketSync\TicketSync.exe
   → La app aplica las migraciones automáticamente en el primer arranque
```

---

## 7. Estructura del proyecto

```
ticketsync/
├── docker-compose.yml               # Configuración de PostgreSQL para desarrollo
├── pom.xml                          # Configuración de Maven
├── scripts/
│   ├── package.ps1                  # Empaquetado Windows (launcher + jpackage opcional)
│   └── package.sh                   # Empaquetado Linux/macOS (launcher + jpackage opcional)
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── module-info.java     # Descriptor del módulo JPMS
    │   │   └── com/ticketsync/
    │   │       ├── App.java         # Punto de entrada JavaFX
    │   │       ├── controller/      # Controladores FXML
    │   │       ├── dao/             # Capa de acceso a datos (patrón DAO)
    │   │       ├── model/           # Entidades de dominio
    │   │       ├── service/         # Lógica de negocio
    │   │       ├── util/            # Utilidades (DatabaseConfig, etc.)
    │   │       ├── viewmodel/       # ViewModels (patrón MVVM)
    │   │       └── exception/       # Excepciones personalizadas
    │   └── resources/
    │       ├── jdbc.properties      # Credenciales de base de datos
    │       ├── log4j2.xml           # Configuración de logging
    │       ├── com/ticketsync/      # Vistas FXML y estilos CSS
    │       └── db/migration/        # Scripts SQL Flyway (V001–V005)
    └── test/
        └── java/                    # Tests JUnit 5
```

### Stack tecnológico

| Componente | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java | 21 LTS |
| UI | JavaFX + AtlantaFX | 21.0.2 / 2.1.0 |
| Base de datos | PostgreSQL + JDBC | 15+ / 42.7.2 |
| Pool de conexiones | HikariCP | 5.1.0 |
| Migraciones | Flyway | 10.8.1 |
| Generación PDF | Apache PDFBox | 3.0.1 |
| Hashing contraseñas | jBCrypt | 0.4 |
| Build | Maven | 3.9+ |
| Tests | JUnit 5 + Mockito | 5.10.0 / 5.11.0 |

---

## 8. Preguntas frecuentes

### La aplicación no puede conectar a la base de datos

Comprueba que PostgreSQL está corriendo y accesible en `localhost:5432`:

```bash
# Con Docker
docker compose ps
docker compose up -d   # si no está corriendo

# Con psql
psql -U postgres -h localhost -c "SELECT 1;"
```

Si usas una configuración de red diferente, crea el fichero `~/.ticketsync/config/jdbc.properties` con los valores correctos (sobreescribe los valores del classpath):

```properties
jdbc.url=jdbc:postgresql://mi-servidor:5432/ticketsync
jdbc.username=mi-usuario
jdbc.password=mi-contraseña
```

---

### ¿Cómo cambio la contraseña de un usuario?

Desde el panel de administración → pestaña **Usuarios** → selecciona el usuario → **Editar usuario**. La nueva contraseña se hashea con BCrypt (coste 12) automáticamente.

También puedes hacerlo directamente en SQL si necesitas restablecer la contraseña del `admin`:

```sql
-- Genera un hash con una herramienta BCrypt (coste 12) y sustitúyelo aquí
UPDATE users SET password_hash = '$2a$12$...' WHERE username = 'admin';
```

---

### ¿Cómo creo más vendedores?

Desde el panel de administración → pestaña **Usuarios** → **Crear usuario** → selecciona el rol `VENDOR`. Los vendedores solo tienen acceso al punto de venta (POS).

---

### Las migraciones de Flyway dan un error de checksum

Si has modificado manualmente un fichero de migración ya aplicado:

```bash
mvn flyway:repair     # Recalcula los checksums en la tabla flyway_schema_history
mvn flyway:validate   # Verifica que todo esté correcto
```

> ⚠️ Nunca modifiques una migración ya aplicada en producción; crea siempre una nueva migración.

---

### ¿La aplicación funciona sin conexión a internet?

Sí. TicketSync está diseñado para operar en red local sin necesidad de internet. Solo requiere conectividad con el servidor PostgreSQL en la red local (o `localhost`).

---

### ¿Cuántas taquillas puede gestionar simultáneamente?

Hasta 10 taquillas simultáneas (pool de 5 conexiones por instancia, ~50 conexiones totales). El sistema garantiza cero sobreventas mediante transacciones `SERIALIZABLE` en PostgreSQL y notificaciones en tiempo real por `LISTEN/NOTIFY`.

---

### ¿Dónde se guardan los logs de la aplicación?

Los logs se guardan en `~/.ticketsync/logs/`:
- **Windows:** `C:\Users\<usuario>\.ticketsync\logs\`
- **Linux / macOS:** `~/.ticketsync/logs/`

El nivel de log se configura en `src/main/resources/log4j2.xml`.
