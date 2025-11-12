import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


def test_root_endpoint():
    """Test the root endpoint"""
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"message": "Welcome to Luxe Jewelry Store API"}

def test_health_check():
    """Test the health check endpoint"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "backend"
    assert data["version"] == "1.1.0"
    assert "timestamp" in data
    assert data["uptime"] == "running"
    assert data["database"] == "connected"
    assert data["environment"] == "production"


def test_get_products():
    """Test getting all products"""
    response = client.get("/api/products")
    assert response.status_code == 200
    products = response.json()
    assert isinstance(products, list)
    assert len(products) > 0
    # Check first product structure
    product = products[0]
    assert "id" in product
    assert "name" in product
    assert "price" in product
    assert "category" in product


def test_get_products_by_category():
    """Test filtering products by category"""
    response = client.get("/api/products?category=rings")
    assert response.status_code == 200
    products = response.json()
    assert isinstance(products, list)
    # All products should be rings
    for product in products:
        assert product["category"] == "rings"


def test_get_single_product():
    """Test getting a single product"""
    response = client.get("/api/products/1")
    assert response.status_code == 200
    product = response.json()
    assert product["id"] == 1
    assert "name" in product
    assert "price" in product


def test_get_nonexistent_product():
    """Test getting a product that doesn't exist"""
    response = client.get("/api/products/999")
    assert response.status_code == 404
    assert response.json()["detail"] == "Product not found"


def test_get_categories():
    """Test getting product categories"""
    response = client.get("/api/categories")
    assert response.status_code == 200
    data = response.json()
    assert "categories" in data
    assert isinstance(data["categories"], list)
    assert len(data["categories"]) > 0


def test_get_stats():
    """Test getting store statistics"""
    response = client.get("/api/stats")
    assert response.status_code == 200
    data = response.json()
    assert "total_products" in data
    assert "total_categories" in data
    assert "categories" in data
    assert "total_inventory_value" in data
    assert "average_price" in data
    assert "status" in data
    assert data["status"] == "active"
    assert isinstance(data["total_products"], int)
    assert isinstance(data["total_categories"], int)
    assert isinstance(data["categories"], list)
    assert data["total_products"] > 0
    assert data["total_categories"] > 0
