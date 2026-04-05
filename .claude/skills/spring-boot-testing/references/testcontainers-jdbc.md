# Testcontainers JDBC

Testing JPA repositories with real databases using Testcontainers.

## Overview

Testcontainers provides real database instances in Docker containers for integration testing. More reliable than H2 for production parity.

## PostgreSQL Setup

### Dependencies

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers-postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

### Recommended Setup (Spring Bean Approach)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DatabaseConfig.class)
class OrderRepositoryPostgresTest {
  
  @Autowired
  private OrderRepository orderRepository;
  
  @Autowired
  private TestEntityManager entityManager;
  
  @Test
  void myTest() { ... }
}

@TestConfiguration(proxyBeanMethods = false)
class DatabaseConfig {
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer("postgres:18");
  }
}
```

### Alternative Setup (JUnit Extension)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryPostgresTest {
  
  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
}
```

## MySQL Setup

```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers-mysql</artifactId>
  <scope>test</scope>
</dependency>
```

```java
@Bean
@ServiceConnection
MySQLContainer mysqlContainer() {
  return new MySQLContainer("mysql:8.4");
}
```

## Multiple Databases

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MultiDatabaseTest {

@TestConfiguration(proxyBeanMethods = false)
class MultiDatabaseConfig {
  
  @Bean
  @ServiceConnection(name = "primary")
  PostgreSQLContainer primaryDb() {
    return new PostgreSQLContainer("postgres:18");
  }
  
  @Bean
  @ServiceConnection(name = "analytics")
  PostgreSQLContainer analyticsDb() {
    return new PostgreSQLContainer("postgres:18");
  }
}
```

## Container Reuse (Speed Optimization)

Add to `~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

Then enable reuse in code:

```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
  return new PostgreSQLContainer("postgres:18")
    .withReuse(true);
}
```

## Database Initialization

### With SQL Scripts

```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
  return new PostgreSQLContainer("postgres:18")
    .withInitScript("schema.sql");
}
```

### With Flyway

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MigrationConfig.class)
class MigrationTest {
  
  @Autowired
  private Flyway flyway;
  
  @Test
  void shouldApplyMigrations() {
    flyway.migrate();
    // Test code
  }
}

@TestConfiguration(proxyBeanMethods = false)
class MigrationConfig {
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer("postgres:18");
  }
}
```

## Advanced Configuration

### Custom Database/Schema

```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
  .withDatabaseName("testdb")
  .withUsername("testuser")
  .withPassword("testpass")
  .withInitScript("init-schema.sql");
```

### Wait Strategies

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
  .waitingFor(Wait.forLogMessage(".*database system is ready.*", 1));
```

## Test Example

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  void shouldFindOrdersByStatus() {
    // Given
    entityManager.persist(new Order("PENDING"));
    entityManager.persist(new Order("COMPLETED"));
    entityManager.flush();

    // When
    List<Order> pending = orderRepository.findByStatus("PENDING");

    // Then
    assertThat(pending).hasSize(1);
    assertThat(pending.get(0).getStatus()).isEqualTo("PENDING");
  }

  @Test
  void shouldSupportPostgresSpecificFeatures() {
    // Can use Postgres-specific features like:
    // - JSONB columns
    // - Array types
    // - Full-text search
  }
}
```

## @DynamicPropertySource Alternative

If not using @ServiceConnection:

```java
@SpringBootTest
@Import(DynamicConfig.class)
class OrderServiceTest {
  // Testing with dynamic properties
}

@TestConfiguration(proxyBeanMethods = false)
class DynamicConfig {
  @Bean
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer("postgres:18");
  }

  @Bean
  DynamicPropertyRegistrar dynamicPropertyRegistrar(PostgreSQLContainer postgres) {
    return (registry) -> {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", postgres::getUsername);
      registry.add("spring.datasource.password", postgres::getPassword);
    };
  }
}
```

## Supported Databases

| Database   | Container Class      | Maven Artifact             |
| ---------- | -------------------- | -------------------------- |
| PostgreSQL | PostgreSQLContainer  | testcontainers-postgresql  |
| MySQL      | MySQLContainer       | testcontainers-mysql       |
| MariaDB    | MariaDBContainer     | testcontainers-mariadb     |
| SQL Server | MSSQLServerContainer | testcontainers-mssqlserver |
| Oracle     | OracleContainer      | testcontainers-oracle-free |
| MongoDB    | MongoDBContainer     | testcontainers-mongodb     |

## Best Practices

1. Use **Spring Bean approach** (@TestConfiguration) for safe container lifecycle management
2. Use **Service Connections** (@ServiceConnection) when possible (Spring Boot 3.1+)
3. Use **specific versions** (e.g., `postgres:18`) instead of `latest`
4. Use **raw types** (e.g., `PostgreSQLContainer`) for specialized containers; use `GenericContainer<?>` only for untyped containers
5. Enable container reuse in local environment for faster build cycles
6. Always ensure **AutoConfigureTestDatabase.Replace.NONE** is used with real containers
