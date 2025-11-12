from fastapi import FastAPI, HTTPException, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from typing import List, Optional
import uuid
import jwt
import httpx
from datetime import datetime
import os

app = FastAPI(title="Luxe Jewelry Store API", version="1.1.0")

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
    id: int
    name: str
    price: float
    image: str
    description: str
    category: str = "jewelry"
    in_stock: bool = True


class CartItem(BaseModel):
    id: str
    product_id: int
    quantity: int
    added_at: datetime

class CartItemRequest(BaseModel):
    product_id: int
    quantity: int = 1


class CartResponse(BaseModel):
    items: List[dict]
    total: float
    item_count: int

# In-memory storage (in production, use a database)
products_db = [
    {
        "id": 1,
        "name": "Diamond Engagement Ring",
        "price": 2999.00,
        "image": "https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=300&h=300&fit=crop",
        "description": "Elegant 1.5 carat diamond ring in 18k white gold",
        "category": "rings",
        "in_stock": True
    },
    {
        "id": 2,
        "name": "Pearl Necklace",
        "price": 899.00,
        "image": "https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?w=300&h=300&fit=crop",
        "description": "Classic freshwater pearl necklace with sterling silver clasp",
        "category": "necklaces",
        "in_stock": True
    },
    {
        "id": 3,
        "name": "Gold Bracelet",
        "price": 1299.00,
        "image": "https://images.unsplash.com/photo-1611591437281-460bfbe1220a?w=300&h=300&fit=crop",
        "description": "Handcrafted 14k gold chain bracelet",
        "category": "bracelets",
        "in_stock": True
    },
    {
        "id": 4,
        "name": "Sapphire Earrings",
        "price": 1599.00,
        "image": "https://images.unsplash.com/photo-1535632066927-ab7c9ab60908?w=300&h=300&fit=crop",
        "description": "Blue sapphire stud earrings in white gold setting",
        "category": "earrings",
        "in_stock": True
    },
    {
        "id": 5,
        "name": "Ruby Tennis Bracelet",
        "price": 3499.00,
        "image": "https://images.unsplash.com/photo-1573408301185-9146fe634ad0?w=300&h=300&fit=crop",
        "description": "Stunning ruby tennis bracelet with 18k white gold setting",
        "category": "bracelets",
        "in_stock": True
    },
    {
        "id": 6,
        "name": "Emerald Pendant",
        "price": 2199.00,
        "image": "https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=300&h=300&fit=crop",
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
        payload = jwt.decode(credentials.credentials, JWT_SECRET_KEY, algorithms=[ALGORITHM])
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
            headers = {"Authorization": f"Bearer {jwt.encode({'sub': user_id}, JWT_SECRET_KEY, algorithm=ALGORITHM)}"}
            response = await client.get(f"{AUTH_SERVICE_URL}/auth/me", headers=headers)
            if response.status_code == 200:
                return response.json()
    except Exception:
        pass
    return None

# In-memory cart storage (in production, use database with user sessions)
carts_db = {}
# User-based carts (for authenticated users)
user_carts_db = {}

# API Endpoints


@app.get("/")
async def root():
    return {"message": "Welcome to Luxe Jewelry Store API"}


@app.get("/health")
async def health_check():
    """Health check endpoint for monitoring and CI/CD"""
    return {
        "status": "healthy",
        "service": "backend",
        "version": "1.1.0",
        "timestamp": datetime.now().isoformat(),
        "uptime": "running",
        "database": "connected",
        "environment": "production",
    }


@app.get("/api/products", response_model=List[Product])
async def get_products(category: Optional[str] = None):
    """Get all products or filter by category"""
    if category:
        filtered_products = [p for p in products_db if p["category"] == category]
        return filtered_products
    return products_db

@app.get("/api/products/{product_id}", response_model=Product)
async def get_product(product_id: int):
    """Get a specific product by ID"""
    product = next((p for p in products_db if p["id"] == product_id), None)
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    return product

@app.post("/api/cart/{session_id}/add")
async def add_to_cart(
    session_id: str, 
    item: CartItemRequest,
    current_user: dict = Depends(get_current_user)
):
    """Add item to cart for authenticated or anonymous user"""
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
    existing_item = next((cart_item for cart_item in cart 
                         if cart_item["product_id"] == item.product_id), None)
    
    if existing_item:
        # Update quantity if item exists
        existing_item["quantity"] += item.quantity
    else:
        # Add new item to cart
        cart_item = {
            "id": str(uuid.uuid4()),
            "product_id": item.product_id,
            "quantity": item.quantity,
            "added_at": datetime.now().isoformat()
        }
        cart.append(cart_item)
    
    return {"message": "Item added to cart", "cart_items": len(cart)}

@app.get("/api/cart", response_model=List[CartItem])
async def get_cart(
    session_id: str = "default",
    current_user: dict = Depends(get_current_user)
):
    """Get cart items for a session or authenticated user"""
    if current_user:
        # Return user's cart if authenticated
        user_id = current_user["id"]
        return user_carts_db.get(user_id, [])
    else:
        # Return session-based cart for anonymous users
        return carts_db.get(session_id, [])

@app.delete("/api/cart/{session_id}/item/{item_id}")
async def remove_from_cart(
    session_id: str, 
    item_id: str,
    current_user: dict = Depends(get_current_user)
):
    """Remove item from cart for authenticated or anonymous user"""
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

@app.put("/api/cart/{session_id}/item/{item_id}")
async def update_cart_item(
    session_id: str, 
    item_id: str, 
    quantity: int,
    current_user: dict = Depends(get_current_user)
):
    """Update cart item quantity for authenticated or anonymous user"""
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

@app.delete("/api/cart/{session_id}")
async def clear_cart(
    session_id: str,
    current_user: dict = Depends(get_current_user)
):
    """Clear entire cart for authenticated or anonymous user"""
    # Determine which cart to use
    if current_user:
        user_id = current_user["id"]
        if user_id in user_carts_db:
            user_carts_db[user_id] = []
    else:
        if session_id in carts_db:
            carts_db[session_id] = []
    return {"message": "Cart cleared"}

@app.get("/api/categories")
async def get_categories():
    """Get all product categories"""
    categories = list(set(p["category"] for p in products_db))
    return {"categories": categories}


@app.get("/api/stats")
async def get_stats():
    """Get store statistics for dashboard"""
    total_products = len(products_db)
    categories = list(set(p["category"] for p in products_db))
    total_value = sum(p["price"] for p in products_db)
    
    return {
        "total_products": total_products,
        "total_categories": len(categories),
        "categories": categories,
        "total_inventory_value": round(total_value, 2),
        "average_price": round(total_value / total_products, 2) if total_products > 0 else 0,
        "status": "active",
        "last_updated": datetime.now().isoformat()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
