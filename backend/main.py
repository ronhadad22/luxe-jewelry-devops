import os
import uuid
from datetime import datetime
from typing import List, Optional

import httpx
import jwt
from fastapi import Depends, FastAPI, HTTPException, Path, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field

tags_metadata = [
    {
        "name": "Health",
        "description": "Health check and readiness endpoints for monitoring and Kubernetes probes.",
    },
    {
        "name": "Products",
        "description": "Browse and search the jewelry catalog. Filter by category or get individual product details.",
    },
    {
        "name": "Cart",
        "description": "Shopping cart operations. Supports both authenticated users and anonymous sessions.",
    },
    {
        "name": "Categories",
        "description": "Product category management and listing.",
    },
    {
        "name": "Statistics",
        "description": "Store analytics and dashboard statistics.",
    },
]

app = FastAPI(
    title="Luxe Jewelry Store API",
    description="""
## Luxe Jewelry Store Backend API

A premium jewelry e-commerce API providing:

* **Product Catalog** - Browse rings, necklaces, bracelets, and earrings
* **Shopping Cart** - Add, update, and remove items with session or user-based persistence
* **Authentication Integration** - Seamless integration with the auth service for user-specific carts

### Authentication
Most endpoints support optional JWT Bearer authentication. Authenticated users get persistent carts tied to their account.
    """,
    version="1.1.1",
    contact={
        "name": "Luxe Jewelry Support",
        "email": "support@luxejewelry.com",
    },
    license_info={
        "name": "MIT",
    },
    openapi_tags=tags_metadata,
)

# Enable CORS for React frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],  # React dev server
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Auth service configuration
AUTH_SERVICE_URL = os.getenv("AUTH_SERVICE_URL", "http://localhost:8001")
JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "your-secret-key-change-in-production")
ALGORITHM = "HS256"

security = HTTPBearer(auto_error=False)


# Data Models
class Product(BaseModel):
    id: int = Field(..., example=1, description="Unique product identifier")
    name: str = Field(..., example="Diamond Engagement Ring", description="Product name")
    price: float = Field(..., example=2999.00, description="Price in USD")
    image: str = Field(..., example="https://images.unsplash.com/photo-1605100804763-247f67b3557e", description="Product image URL")
    description: str = Field(..., example="Elegant 1.5 carat diamond ring in 18k white gold", description="Product description")
    category: str = Field(default="jewelry", example="rings", description="Product category")
    in_stock: bool = Field(default=True, example=True, description="Stock availability")

    class Config:
        json_schema_extra = {
            "example": {
                "id": 1,
                "name": "Diamond Engagement Ring",
                "price": 2999.00,
                "image": "https://images.unsplash.com/photo-1605100804763-247f67b3557e",
                "description": "Elegant 1.5 carat diamond ring in 18k white gold",
                "category": "rings",
                "in_stock": True
            }
        }


class CartItem(BaseModel):
    id: str = Field(..., example="550e8400-e29b-41d4-a716-446655440000", description="Cart item UUID")
    product_id: int = Field(..., example=1, description="Product ID reference")
    quantity: int = Field(..., example=2, description="Quantity in cart")
    added_at: datetime = Field(..., description="Timestamp when item was added")

    class Config:
        json_schema_extra = {
            "example": {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "product_id": 1,
                "quantity": 2,
                "added_at": "2024-01-15T10:30:00"
            }
        }


class CartItemRequest(BaseModel):
    product_id: int = Field(..., example=1, description="Product ID to add")
    quantity: int = Field(default=1, example=1, ge=1, description="Quantity to add")

    class Config:
        json_schema_extra = {
            "example": {
                "product_id": 1,
                "quantity": 2
            }
        }


class CartResponse(BaseModel):
    items: List[dict] = Field(..., description="List of cart items with product details")
    total: float = Field(..., example=5998.00, description="Total cart value in USD")
    item_count: int = Field(..., example=2, description="Total number of items")


# In-memory storage (in production, use a database)
products_db = [
    {
        "id": 1,
        "name": "Diamond Engagement Ring",
        "price": 2999.00,
        "image": (
            "https://images.unsplash.com/photo-1605100804763-247f67b3557e"
            "?w=300&h=300&fit=crop"
        ),
        "description": "Elegant 1.5 carat diamond ring in 18k white gold",
        "category": "rings",
        "in_stock": True,
    },
    {
        "id": 2,
        "name": "Pearl Necklace",
        "price": 899.00,
        "image": (
            "https://images.unsplash.com/photo-1515562141207-7a88fb7ce338"
            "?w=300&h=300&fit=crop"
        ),
        "description": "Classic freshwater pearl necklace with sterling silver clasp",
        "category": "necklaces",
        "in_stock": True,
    },
    {
        "id": 3,
        "name": "Gold Bracelet",
        "price": 1299.00,
        "image": (
            "https://images.unsplash.com/photo-1611591437281-460bfbe1220a"
            "?w=300&h=300&fit=crop"
        ),
        "description": "Handcrafted 14k gold chain bracelet",
        "category": "bracelets",
        "in_stock": True,
    },
    {
        "id": 4,
        "name": "Sapphire Earrings",
        "price": 1599.00,
        "image": (
            "https://images.unsplash.com/photo-1535632066927-ab7c9ab60908"
            "?w=300&h=300&fit=crop"
        ),
        "description": "Blue sapphire stud earrings in white gold setting",
        "category": "earrings",
        "in_stock": True,
    },
    {
        "id": 5,
        "name": "Ruby Tennis Bracelet",
        "price": 3499.00,
        "image": (
            "https://images.unsplash.com/photo-1573408301185-9146fe634ad0"
            "?w=300&h=300&fit=crop"
        ),
        "description": "Stunning ruby tennis bracelet with 18k white gold setting",
        "category": "bracelets",
        "in_stock": True,
    },
    {
        "id": 6,
        "name": "Emerald Pendant",
        "price": 2199.00,
        "image": (
            "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f"
            "?w=300&h=300&fit=crop"
        ),
        "description": "Exquisite emerald pendant with diamond accents",
        "category": "necklaces",
        "in_stock": True,
    },
]


# Authentication utilities
async def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """Verify JWT token and return user ID (optional authentication)"""
    if not credentials:
        return None

    try:
        payload = jwt.decode(
            credentials.credentials, JWT_SECRET_KEY, algorithms=[ALGORITHM]
        )
        user_id: str = payload.get("sub")
        return user_id
    except jwt.PyJWTError:
        return None


async def get_current_user(user_id: str = Depends(verify_token)):
    """Get current user info from auth service"""
    if not user_id:
        return None

    try:
        async with httpx.AsyncClient() as client:
            headers = {
                "Authorization": f"Bearer {jwt.encode({'sub': user_id}, JWT_SECRET_KEY, algorithm=ALGORITHM)}"
            }
            response = await client.get(f"{AUTH_SERVICE_URL}/auth/me", headers=headers)
            if response.status_code == 200:
                return response.json()
    except Exception:
        pass


# In-memory cart storage (in production, use database with user sessions)
carts_db = {}
# User-based carts (for authenticated users)
user_carts_db = {}

# API Endpoints


@app.get(
    "/",
    tags=["Health"],
    summary="API Root",
    response_description="Welcome message with API version",
)
async def root():
    """
    Root endpoint returning API welcome message and version info.
    """
    return {
        "message": "Welcome to Luxe Jewelry Store API",
        "version": "1.1.0",
        "feature": "api-improvements",
    }


@app.get(
    "/health",
    tags=["Health"],
    summary="Health Check",
    response_description="Service health status",
    responses={
        200: {
            "description": "Service is healthy",
            "content": {
                "application/json": {
                    "example": {
                        "status": "healthy",
                        "service": "backend",
                        "version": "1.1.0",
                        "timestamp": "2024-01-15T10:30:00",
                        "uptime": "running",
                        "database": "connected",
                        "environment": "production"
                    }
                }
            }
        }
    }
)
async def health_check():
    """Health check endpoint for monitoring and CI/CD."""
    return {
        "status": "healthy",
        "service": "backend",
        "version": "1.1.0",
        "timestamp": datetime.now().isoformat(),
        "uptime": "running",
        "database": "connected",
        "environment": "production",
    }


@app.get(
    "/ready",
    tags=["Health"],
    summary="Readiness Check",
    response_description="Service readiness status",
    responses={
        200: {
            "description": "Service is ready to accept traffic",
            "content": {
                "application/json": {
                    "example": {
                        "status": "ready",
                        "service": "backend",
                        "timestamp": "2024-01-15T10:30:00"
                    }
                }
            }
        }
    }
)
async def readiness_check():
    """
    Readiness check endpoint for Kubernetes readiness probe.
    Returns 200 when the service is ready to accept traffic.
    """
    # Add any initialization checks here (database, cache, etc.)
    # For now, if the app is running, it's ready
    return {
        "status": "ready",
        "service": "backend",
        "timestamp": datetime.now().isoformat(),
    }


@app.get(
    "/api/products",
    response_model=List[Product],
    tags=["Products"],
    summary="List Products",
    response_description="List of jewelry products",
    responses={
        200: {
            "description": "Successfully retrieved products",
            "content": {
                "application/json": {
                    "example": [
                        {
                            "id": 1,
                            "name": "Diamond Engagement Ring",
                            "price": 2999.00,
                            "image": "https://images.unsplash.com/photo-1605100804763-247f67b3557e",
                            "description": "Elegant 1.5 carat diamond ring in 18k white gold",
                            "category": "rings",
                            "in_stock": True
                        }
                    ]
                }
            }
        }
    }
)
async def get_products(
    category: Optional[str] = Query(
        None,
        description="Filter products by category",
        example="rings",
        enum=["rings", "necklaces", "bracelets", "earrings"]
    )
):
    """
    Get all products or filter by category.
    
    - **category**: Optional filter for product category (rings, necklaces, bracelets, earrings)
    """
    if category:
        filtered_products = [p for p in products_db if p["category"] == category]
        return filtered_products
    return products_db


@app.get(
    "/api/products/{product_id}",
    response_model=Product,
    tags=["Products"],
    summary="Get Product by ID",
    response_description="Product details",
    responses={
        200: {"description": "Product found"},
        404: {
            "description": "Product not found",
            "content": {
                "application/json": {
                    "example": {"detail": "Product not found"}
                }
            }
        }
    }
)
async def get_product(
    product_id: int = Path(..., description="Unique product identifier", example=1, ge=1)
):
    """
    Get a specific product by its ID.
    
    - **product_id**: The unique identifier of the product
    """
    product = next((p for p in products_db if p["id"] == product_id), None)
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    return product


@app.post(
    "/api/cart/{session_id}/add",
    tags=["Cart"],
    summary="Add Item to Cart",
    response_description="Confirmation of item added",
    responses={
        200: {
            "description": "Item added successfully",
            "content": {
                "application/json": {
                    "example": {"message": "Item added to cart", "cart_items": 3}
                }
            }
        },
        404: {
            "description": "Product not found",
            "content": {
                "application/json": {
                    "example": {"detail": "Product not found"}
                }
            }
        }
    }
)
async def add_to_cart(
    session_id: str = Path(..., description="Session ID for anonymous users", example="sess-abc123"),
    item: CartItemRequest = ...,
    current_user: dict = Depends(get_current_user),
):
    """
    Add an item to the shopping cart.
    
    - **session_id**: Session identifier for anonymous users (ignored for authenticated users)
    - **item**: Product ID and quantity to add
    
    Authenticated users have their cart tied to their account.
    Anonymous users use session-based carts.
    """
    # Find the product
    product = next((p for p in products_db if p["id"] == item.product_id), None)
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")

    # Determine which cart to use
    if current_user:
        user_id = current_user["id"]
        if user_id not in user_carts_db:
            user_carts_db[user_id] = []
        cart = user_carts_db[user_id]
    else:
        if session_id not in carts_db:
            carts_db[session_id] = []
        cart = carts_db[session_id]

    # Check if item already exists in cart
    existing_item = next(
        (cart_item for cart_item in cart if cart_item["product_id"] == item.product_id),
        None,
    )

    if existing_item:
        # Update quantity if item exists
        existing_item["quantity"] += item.quantity
    else:
        # Add new item to cart
        cart_item = {
            "id": str(uuid.uuid4()),
            "product_id": item.product_id,
            "quantity": item.quantity,
            "added_at": datetime.now().isoformat(),
        }
        cart.append(cart_item)

    return {"message": "Item added to cart", "cart_items": len(cart)}


@app.get(
    "/api/cart",
    response_model=List[CartItem],
    tags=["Cart"],
    summary="Get Cart Contents",
    response_description="List of items in cart",
    responses={
        200: {
            "description": "Cart contents retrieved",
            "content": {
                "application/json": {
                    "example": [
                        {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "product_id": 1,
                            "quantity": 2,
                            "added_at": "2024-01-15T10:30:00"
                        }
                    ]
                }
            }
        }
    }
)
async def get_cart(
    session_id: str = Query("default", description="Session ID for anonymous users", example="sess-abc123"),
    current_user: dict = Depends(get_current_user),
):
    """
    Get all items in the shopping cart.
    
    - **session_id**: Session identifier for anonymous users
    
    Returns the user's cart if authenticated, otherwise returns session-based cart.
    """
    if current_user:
        # Return user's cart if authenticated
        user_id = current_user["id"]
        return user_carts_db.get(user_id, [])
    else:
        # Return session-based cart for anonymous users
        return carts_db.get(session_id, [])


@app.delete(
    "/api/cart/{session_id}/item/{item_id}",
    tags=["Cart"],
    summary="Remove Item from Cart",
    response_description="Confirmation of item removal",
    responses={
        200: {
            "description": "Item removed successfully",
            "content": {
                "application/json": {
                    "example": {"message": "Item removed from cart", "cart_items": 2}
                }
            }
        },
        404: {
            "description": "Cart or item not found",
            "content": {
                "application/json": {
                    "example": {"detail": "Item not found in cart"}
                }
            }
        }
    }
)
async def remove_from_cart(
    session_id: str = Path(..., description="Session ID", example="sess-abc123"),
    item_id: str = Path(..., description="Cart item UUID to remove", example="550e8400-e29b-41d4-a716-446655440000"),
    current_user: dict = Depends(get_current_user)
):
    """
    Remove an item from the shopping cart.
    
    - **session_id**: Session identifier for anonymous users
    - **item_id**: UUID of the cart item to remove
    """
    # Determine which cart to use
    if current_user:
        user_id = current_user["id"]
        if user_id not in user_carts_db:
            raise HTTPException(status_code=404, detail="Cart not found")
        cart = user_carts_db[user_id]
    else:
        if session_id not in carts_db:
            raise HTTPException(status_code=404, detail="Cart not found")
        cart = carts_db[session_id]

    # Find and remove the item
    item_to_remove = next((item for item in cart if item["id"] == item_id), None)

    if not item_to_remove:
        raise HTTPException(status_code=404, detail="Item not found in cart")

    cart.remove(item_to_remove)

    return {"message": "Item removed from cart", "cart_items": len(cart)}


@app.put(
    "/api/cart/{session_id}/item/{item_id}",
    tags=["Cart"],
    summary="Update Cart Item Quantity",
    response_description="Confirmation of quantity update",
    responses={
        200: {
            "description": "Quantity updated successfully",
            "content": {
                "application/json": {
                    "example": {"message": "Item quantity updated"}
                }
            }
        },
        404: {
            "description": "Cart or item not found",
            "content": {
                "application/json": {
                    "example": {"detail": "Item not found in cart"}
                }
            }
        }
    }
)
async def update_cart_item(
    session_id: str = Path(..., description="Session ID", example="sess-abc123"),
    item_id: str = Path(..., description="Cart item UUID", example="550e8400-e29b-41d4-a716-446655440000"),
    quantity: int = Query(..., description="New quantity (0 to remove)", example=3, ge=0),
    current_user: dict = Depends(get_current_user),
):
    """
    Update the quantity of an item in the cart.
    
    - **session_id**: Session identifier for anonymous users
    - **item_id**: UUID of the cart item to update
    - **quantity**: New quantity (set to 0 to remove the item)
    """
    # Determine which cart to use
    if current_user:
        user_id = current_user["id"]
        if user_id not in user_carts_db:
            raise HTTPException(status_code=404, detail="Cart not found")
        cart = user_carts_db[user_id]
    else:
        if session_id not in carts_db:
            raise HTTPException(status_code=404, detail="Cart not found")
        cart = carts_db[session_id]

    # Find the item
    item = next((item for item in cart if item["id"] == item_id), None)

    if not item:
        raise HTTPException(status_code=404, detail="Item not found in cart")

    if quantity <= 0:
        # Remove item if quantity is 0 or negative
        cart.remove(item)
        return {"message": "Item removed from cart"}
    else:
        item["quantity"] = quantity
        return {"message": "Item quantity updated"}


@app.delete(
    "/api/cart/{session_id}",
    tags=["Cart"],
    summary="Clear Cart",
    response_description="Confirmation of cart cleared",
    responses={
        200: {
            "description": "Cart cleared successfully",
            "content": {
                "application/json": {
                    "example": {"message": "Cart cleared"}
                }
            }
        }
    }
)
async def clear_cart(
    session_id: str = Path(..., description="Session ID", example="sess-abc123"),
    current_user: dict = Depends(get_current_user)
):
    """
    Clear all items from the shopping cart.
    
    - **session_id**: Session identifier for anonymous users
    """
    # Determine which cart to use
    if current_user:
        user_id = current_user["id"]
        if user_id in user_carts_db:
            user_carts_db[user_id] = []
    else:
        if session_id in carts_db:
            carts_db[session_id] = []
    return {"message": "Cart cleared"}


@app.get(
    "/api/categories",
    tags=["Categories"],
    summary="List Categories",
    response_description="Available product categories",
    responses={
        200: {
            "description": "Categories retrieved successfully",
            "content": {
                "application/json": {
                    "example": {"categories": ["rings", "necklaces", "bracelets", "earrings"]}
                }
            }
        }
    }
)
async def get_categories():
    """
    Get all available product categories.
    
    Returns a list of unique categories from the product catalog.
    """
    categories = list(set(p["category"] for p in products_db))
    return {"categories": categories}


@app.get(
    "/api/stats",
    tags=["Statistics"],
    summary="Store Statistics",
    response_description="Store analytics and metrics",
    responses={
        200: {
            "description": "Statistics retrieved successfully",
            "content": {
                "application/json": {
                    "example": {
                        "total_products": 6,
                        "total_categories": 4,
                        "categories": ["rings", "necklaces", "bracelets", "earrings"],
                        "total_inventory_value": 12494.00,
                        "average_price": 2082.33,
                        "status": "active",
                        "last_updated": "2024-01-15T10:30:00"
                    }
                }
            }
        }
    }
)
async def get_stats():
    """
    Get store statistics for the dashboard.
    
    Returns aggregate metrics including:
    - Total product count
    - Category breakdown
    - Inventory value
    - Average product price
    """
    total_products = len(products_db)
    categories = list(set(p["category"] for p in products_db))
    total_value = sum(p["price"] for p in products_db)

    return {
        "total_products": total_products,
        "total_categories": len(categories),
        "categories": categories,
        "total_inventory_value": round(total_value, 2),
        "average_price": (
            round(total_value / total_products, 2) if total_products > 0 else 0
        ),
        "status": "active",
        "last_updated": datetime.now().isoformat(),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
