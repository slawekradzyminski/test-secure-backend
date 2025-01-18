# E-commerce API Design

## Authentication
All endpoints require authentication via JWT token in the Authorization header:
```http
Authorization: Bearer <token>
```

## Models

### Product
```typescript
{
  id: number;
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  category: string;
  imageUrl: string;
  createdAt: string;
  updatedAt: string;
}
```

### CartItem
```typescript
{
  id: number;
  productId: number;
  quantity: number;
  price: number;  // price at the time of adding to cart
}
```

### Order
```typescript
{
  id: number;
  userId: number;
  items: OrderItem[];
  totalAmount: number;
  status: 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
  shippingAddress: Address;
  createdAt: string;
  updatedAt: string;
}
```

### OrderItem
```typescript
{
  id: number;
  productId: number;
  quantity: number;
  price: number;  // price at the time of order
}
```

### Address
```typescript
{
  street: string;
  city: string;
  state: string;
  zipCode: string;
  country: string;
}
```

## API Endpoints

### Products

#### Get All Products (Authenticated)
```http
GET /api/products
Authorization: Required
Query Parameters:
  - page: number (default: 0)
  - size: number (default: 20)
  - category: string (optional)
  - minPrice: number (optional)
  - maxPrice: number (optional)
  - sortBy: string (name, price) (default: name)
  - sortDir: string (asc, desc) (default: asc)
Response: 200 OK
{
  content: Product[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

Error Response: 401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

#### Get Product by ID (Authenticated)
```http
GET /api/products/{id}
Authorization: Required
Response: 200 OK
Product

Error Response: 401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

#### Create Product (Admin only)
```http
POST /api/products
Body: {
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  category: string;
  imageUrl: string;
}
Response: 201 Created
Product
```

#### Update Product (Admin only)
```http
PUT /api/products/{id}
Body: {
  name?: string;
  description?: string;
  price?: number;
  stockQuantity?: number;
  category?: string;
  imageUrl?: string;
}
Response: 200 OK
Product
```

#### Delete Product (Admin only)
```http
DELETE /api/products/{id}
Response: 204 No Content
```

### Shopping Cart

#### Get Cart
```http
GET /api/cart
Response: 200 OK
{
  items: CartItem[];
  totalAmount: number;
}
```

#### Add Item to Cart
```http
POST /api/cart/items
Body: {
  productId: number;
  quantity: number;
}
Response: 200 OK
{
  items: CartItem[];
  totalAmount: number;
}
```

#### Update Cart Item Quantity
```http
PUT /api/cart/items/{productId}
Body: {
  quantity: number;
}
Response: 200 OK
{
  items: CartItem[];
  totalAmount: number;
}
```

#### Remove Item from Cart
```http
DELETE /api/cart/items/{productId}
Response: 200 OK
{
  items: CartItem[];
  totalAmount: number;
}
```

#### Clear Cart
```http
DELETE /api/cart
Response: 204 No Content
```

### Orders

#### Create Order
```http
POST /api/orders
Body: {
  shippingAddress: Address;
}
Response: 201 Created
Order
```

#### Get User Orders
```http
GET /api/orders
Query Parameters:
  - page: number (default: 0)
  - size: number (default: 20)
  - status: string (optional)
Response: 200 OK
{
  content: Order[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
```

#### Get Order by ID
```http
GET /api/orders/{id}
Response: 200 OK
Order
```

#### Update Order Status (Admin only)
```http
PUT /api/orders/{id}/status
Body: {
  status: 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
}
Response: 200 OK
Order
```

#### Cancel Order
```http
POST /api/orders/{id}/cancel
Response: 200 OK
Order
```

## Error Responses

```http
401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}

403 Forbidden
{
  "error": "Forbidden",
  "message": "Access is denied"
}

400 Bad Request
{
  "error": "Validation failed",
  "details": {
    "field": "error message"
  }
}

404 Not Found
{
  "error": "Not Found",
  "message": "Resource not found"
}

409 Conflict
{
  "error": "Product out of stock"
}

500 Internal Server Error
{
  "error": "Internal server error"
}
```

## Security

- All endpoints except GET /api/products* require authentication
- Admin endpoints require ROLE_ADMIN
- Users can only access their own cart and orders
- Order status updates are restricted to admins
- Product creation/updates/deletion restricted to admins 