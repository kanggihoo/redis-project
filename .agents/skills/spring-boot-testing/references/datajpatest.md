# @DataJpaTest

Testing JPA repositories with isolated data layer slice.

## Basic Structure

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MyTestConfiguration.class) // Container configuration
class OrderRepositoryTest {
  
  @Autowired
  private OrderRepository orderRepository;
  
  @Autowired
  private TestEntityManager entityManager;
  
  @Test
  void myTest() { ... }
}

@TestConfiguration(proxyBeanMethods = false)
class MyTestConfiguration {
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:18");
	}
}
```

- Repository beans
- EntityManager / TestEntityManager
- DataSource (Auto-configured via Service Connections)
- Flyway / Liquibase (Auto-configured connection if present)
- Transaction manager
- No web layer, no services, no controllers

## Testing Custom Queries

```java
@Test
void shouldFindOrdersByStatus() {
  // Given - Using var for cleaner code
  var pending = new Order("PENDING");
  var completed = new Order("COMPLETED");
  entityManager.persist(pending);
  entityManager.persist(completed);
  entityManager.flush();
  
  // When
  var pendingOrders = orderRepository.findByStatus("PENDING");
  
  // Then - Using sequenced collection methods
  assertThat(pendingOrders).hasSize(1);
  assertThat(pendingOrders.getFirst().getStatus()).isEqualTo("PENDING");
}
```

## Testing Native Queries

```java
@Test
void shouldExecuteNativeQuery() {
  entityManager.persist(new Order("PENDING", BigDecimal.valueOf(100)));
  entityManager.persist(new Order("PENDING", BigDecimal.valueOf(200)));
  entityManager.flush();
  
  var total = orderRepository.calculatePendingTotal();
  
  assertThat(total).isEqualTo(new BigDecimal("300.00"));
}
```

## Testing Pagination

```java
@Test
void shouldReturnPagedResults() {
  // Insert 20 orders using IntStream
  IntStream.range(0, 20).forEach(i -> {
    entityManager.persist(new Order("PENDING"));
  });
  entityManager.flush();
  
  var page = orderRepository.findByStatus("PENDING", PageRequest.of(0, 10));
  
  assertThat(page.getContent()).hasSize(10);
  assertThat(page.getTotalElements()).isEqualTo(20);
  assertThat(page.getContent().getFirst().getStatus()).isEqualTo("PENDING");
}
```

## Testing Lazy Loading

```java
@Test
void shouldLazyLoadOrderItems() {
  var order = new Order("PENDING");
  order.addItem(new OrderItem("Product", 2));
  entityManager.persist(order);
  entityManager.flush();
  entityManager.clear(); // Detach from persistence context
  
  var found = orderRepository.findById(order.getId());
  
  assertThat(found).isPresent();
  // This will trigger lazy loading
  assertThat(found.get().getItems()).hasSize(1);
  assertThat(found.get().getItems().getFirst().getProduct()).isEqualTo("Product");
}
```

## Testing Cascading

```java
@Test
void shouldCascadeDelete() {
  var order = new Order("PENDING");
  order.addItem(new OrderItem("Product", 2));
  entityManager.persist(order);
  entityManager.flush();
  
  orderRepository.delete(order);
  entityManager.flush();
  
  assertThat(entityManager.find(OrderItem.class, order.getItems().getFirst().getId()))
    .isNull();
}
```

## Testing @Query Methods

```java
@Query("SELECT o FROM Order o WHERE o.createdAt > :date AND o.status = :status")
List<Order> findRecentByStatus(@Param("date") LocalDateTime date, 
                               @Param("status") String status);

@Test
void shouldFindRecentOrders() {
  var old = new Order("PENDING");
  old.setCreatedAt(LocalDateTime.now().minusDays(10));
  var recent = new Order("PENDING");
  recent.setCreatedAt(LocalDateTime.now().minusHours(1));
  
  entityManager.persist(old);
  entityManager.persist(recent);
  entityManager.flush();
  
  var recentOrders = orderRepository.findRecentByStatus(
    LocalDateTime.now().minusDays(1), "PENDING");
  
  assertThat(recentOrders).hasSize(1);
  assertThat(recentOrders.getFirst().getId()).isEqualTo(recent.getId());
}
```

## Using H2 vs Real Database

### H2 (Default - Not Recommended for Production Parity)

```java
@DataJpaTest // Uses embedded H2 by default
class OrderRepositoryH2Test {
  // Fast but may miss DB-specific issues
}
```

### Testcontainers (Recommended - Spring Bean Approach)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContainerConfig.class)
class OrderRepositoryPostgresTest {
  // Safe lifecycle management (starts before other beans)
}

@TestConfiguration(proxyBeanMethods = false)
class ContainerConfig {
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer("postgres:18");
  }
}
```

> [!TIP]
> `PostgreSQLContainer`와 같은 전용 컨테이너는 제네릭(`<?>`) 없이 선언하며, `GenericContainer`를 사용할 때만 `GenericContainer<?>`와 같이 선언합니다.

## Transaction Behavior

Tests are @Transactional by default and roll back after each test.

```java
@Test
@Rollback(false) // Don't roll back (rarely needed)
void shouldPersistData() {
  orderRepository.save(new Order("PENDING"));
  // Data will remain in database after test
}
```

1. Use **Spring Bean approach** (@TestConfiguration) for safe container lifecycle management
2. Use **Service Connections** (@ServiceConnection) to avoid manual property mapping
3. Use **TestEntityManager** for setup data to avoid caching issues with Repository save()
4. Always **flush()** after persist() to trigger SQL and check constraints
5. **Clear()** the entity manager when testing lazy loading or detachment
6. Use **@MockitoBean** instead of @MockBean if you need to mock dependencies in @DataJpaTest
7. Leverage Java 21+ **sequenced collection** methods (getFirst(), getLast()) for cleaner assertions
8. Prefer **AssertJ** for readable and expressive test cases
