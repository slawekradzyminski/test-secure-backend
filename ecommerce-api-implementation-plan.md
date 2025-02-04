# E-commerce API Implementation Plan

## Phase 1: Core Models and Database Setup ✅
1. Create JPA entities:
   ```java
   ✅ Product.java
   ✅ CartItem.java
   ✅ Order.java
   ✅ OrderItem.java
   ✅ Address.java (as @Embeddable)
   ```
   Each with corresponding repository interfaces.

2. Create DTOs:
   ```java
   ✅ ProductDTO (request/response)
   ✅ CartItemDTO
   ✅ OrderDTO
   ✅ AddressDTO
   ```
   With proper validation annotations like @Size, @NotNull, etc.

## Phase 2: Product Management ✅
1. ✅ Implement ProductService with methods:
   ```java
   List<Product> getAllProducts();
   Optional<Product> getProductById(Long id);
   Product createProduct(ProductDTO productDTO);
   Optional<Product> updateProduct(Long id, ProductDTO productDTO);
   boolean deleteProduct(Long id);
   ```
   Service layer will handle business logic and transactions.

2. ✅ Implement ProductController with endpoints:
   ```http
   GET    /api/products      (authenticated)
   GET    /api/products/{id} (authenticated)
   POST   /api/products      (ADMIN)
   PUT    /api/products/{id} (ADMIN)
   DELETE /api/products/{id} (ADMIN)
   ```
   Controller will delegate business logic to ProductService.

3. ✅ Create ProductControllerTest following project patterns:
   ```java
   @Test
   void shouldGetAllProductsWhenAuthenticated() {
       // given
       // Create user token and test products
       
       // when
       // GET /api/products with token
       
       // then
       // Assert 200 OK and products list
   }
   
   @Test
   void shouldFailToGetProductsWhenNotAuthenticated() {
       // when
       // GET /api/products without token
       
       // then
       // Assert 401 UNAUTHORIZED
   }
   
   @Test
   void shouldGetProductById() {
       // given
       // Create test product
       
       // when
       // GET /api/products/{id}
       
       // then
       // Assert 200 OK and product details
   }
   
   @Test
   void shouldReturn404ForNonExistingProduct() {
       // when
       // GET /api/products/999
       
       // then
       // Assert 404 Not Found
   }
   
   @Test
   void shouldCreateProductAsAdmin() {
       // given
       // Create admin token and product data
       
       // when
       // POST /api/products
       
       // then
       // Assert 201 Created and product details
   }
   
   @Test
   void shouldFailToCreateProductAsClient() {
       // given
       // Create client token and product data
       
       // when
       // POST /api/products
       
       // then
       // Assert 403 Forbidden
   }
   ```

## Phase 3: Shopping Cart ✅
1. ✅ Implement CartService with methods:
   ```java
   CartDTO getCart(String username);
   CartDTO addToCart(String username, CartItemDTO cartItemDTO);
   CartDTO updateCartItem(String username, Long productId, CartItemDTO cartItemDTO);
   CartDTO removeFromCart(String username, Long productId);
   void clearCart(String username);
   ```
   Service layer will handle business logic and transactions.

2. ✅ Implement CartController with endpoints:
   ```http
   GET    /api/cart                        - Get current user's cart
   POST   /api/cart/items                  - Add item to cart
   PUT    /api/cart/items/{productId}      - Update item quantity
   DELETE /api/cart/items/{productId}      - Remove item from cart
   DELETE /api/cart                        - Clear cart
   ```

3. ✅ Create CartControllerTest:
   ```java
   @Test
   void shouldGetEmptyCart() {
       // given
       // Create user token
       
       // when
       // GET /api/cart
       
       // then
       // Assert 200 OK and empty cart
   }
   
   @Test
   void shouldAddItemToCart() {
       // given
       // Create user token and product
       
       // when
       // POST /api/cart/items
       
       // then
       // Assert 200 OK and updated cart
   }
   
   @Test
   void shouldUpdateCartItemQuantity() {
       // given
       // Create user token and cart with item
       
       // when
       // PUT /api/cart/items/{productId}
       
       // then
       // Assert 200 OK and updated quantity
   }
   ```

4. ✅ Add Swagger/OpenAPI documentation:
   - Added @Tag annotations for controller grouping
   - Added @Operation annotations for endpoint descriptions
   - Added @ApiResponses for documenting response codes
   - Added @SecurityRequirement for JWT authentication

## Phase 4: Order Management ✅
1. ✅ Implement OrderController with endpoints:
   ```http
   POST   /api/orders
   GET    /api/orders
   GET    /api/orders/{id}
   PUT    /api/orders/{id}/status (ADMIN)
   POST   /api/orders/{id}/cancel
   ```

2. ✅ Create OrderControllerTest:
   ```java
   @Test
   void shouldCreateOrder() {
       // given
       // Create user token and cart with items
       
       // when
       // POST /api/orders
       
       // then
       // Assert 201 Created and order details
   }
   
   @Test
   void shouldGetUserOrders() {
       // given
       // Create user token and orders
       
       // when
       // GET /api/orders
       
       // then
       // Assert 200 OK and orders list
   }
   ```

## Phase 5: Future Enhancements 🔄
1. Add payment processing integration
   - Implement PaymentService
   - Add payment endpoints
   - Support multiple payment methods

2. Add order status notifications
   - Implement WebSocket for real-time updates
   - Send email notifications for order status changes

3. Add product categories and search
   - Implement product categorization
   - Add search functionality with filters
   - Support pagination and sorting

4. Add product reviews and ratings
   - Implement review system
   - Add rating functionality
   - Calculate average ratings

5. Add inventory management
   - Track product stock levels
   - Implement low stock notifications
   - Add bulk import/export functionality

## Implementation Strategy
1. For each phase:
   - Create entities and DTOs
   - Write tests first (TDD approach)
   - Implement services and controllers
   - Add Swagger documentation
   - Verify security constraints

2. Reuse existing project patterns:
   ```java
   - Extend HttpHelper for tests
   - Use DomainHelper for test data generation
   - Follow the same security model (JWT)
   - Use similar validation approach
   ```

## Testing Approach
1. Each test class will:
   ```java
   @ActiveProfiles("test")
   public class ProductControllerTest extends DomainHelper {
       // Test methods following given/when/then pattern
       // Both happy and error paths
       // Security constraints verification
   }
   ```

2. Test data generation:
   ```java
   // Add to DomainHelper:
   public Product getRandomProduct() {
       return Product.builder()
           .name(RandomStringUtils.randomAlphanumeric(10))
           .price(new BigDecimal("99.99"))
           // ... other fields
           .build();
   }
   
   // Similar methods for CartItem and Order
   ```

## Security Integration
1. Update WebSecurityConfig:
   ```java
   @Configuration
   public class WebSecurityConfig {
       @Override
       protected void configure(HttpSecurity http) {
           http.authorizeRequests()
               // All endpoints require authentication
               .antMatchers("/api/products/**").authenticated()
               .antMatchers(POST, "/api/products/**").hasRole("ADMIN")
               .antMatchers(PUT, "/api/products/**").hasRole("ADMIN")
               .antMatchers(DELETE, "/api/products/**").hasRole("ADMIN")
               .antMatchers("/api/cart/**").authenticated()
               .antMatchers("/api/orders/**").authenticated()
               .antMatchers(PUT, "/api/orders/*/status").hasRole("ADMIN")
               // ... existing configuration
       }
   }
   ```

2. Test security for each endpoint:
   - Unauthenticated access (expect 401)
   - Client role access (where allowed)
   - Admin role access (where required)

## Implementation Order
1. Phase 1: Core Models (2-3 days)
2. Phase 2: Product Management (2-3 days)
3. Phase 3: Shopping Cart (2-3 days)
4. Phase 4: Order Management (2-3 days)

Total estimated time: 8-12 days depending on complexity and issues encountered.

## Next Steps:
1. Implement Order functionality:
   - Create Order and OrderItem entities
   - Implement OrderService
   - Create OrderController with endpoints
   - Add comprehensive tests
   - Add Swagger documentation 