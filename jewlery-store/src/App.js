import React, { useState, useEffect, useCallback } from 'react';
import './App.css';

// API Configuration
const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8000/api';
const AUTH_BASE_URL = process.env.REACT_APP_AUTH_BASE_URL || 'http://localhost:8001';

// Log the API endpoint for debugging
console.log('🔗 Backend API Endpoint:', API_BASE_URL);
console.log('Auth service endpoint:', AUTH_BASE_URL);

// Generate or get session ID
const getSessionId = () => {
  let sessionId = localStorage.getItem('jewelry_session_id');
  if (!sessionId) {
    sessionId = 'session_' + Math.random().toString(36).substr(2, 9);
    localStorage.setItem('jewelry_session_id', sessionId);
  }
  return sessionId;
};

function App() {
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState([]);
  const [currentPage, setCurrentPage] = useState('home');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [user, setUser] = useState(null);
  const [authToken, setAuthToken] = useState(localStorage.getItem('authToken'));
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'register'
  const sessionId = getSessionId();

  // Authentication Functions
  const getAuthHeaders = useCallback(() => {
    return authToken ? { 'Authorization': `Bearer ${authToken}` } : {};
  }, [authToken]);

  const login = async (email, password) => {
    try {
      setLoading(true);
      const response = await fetch(`${AUTH_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
      });
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.detail || 'Login failed');
      }
      
      const data = await response.json();
      setAuthToken(data.access_token);
      setUser(data.user);
      localStorage.setItem('authToken', data.access_token);
      setShowAuthModal(false);
      setError(null);
      
      // Refresh cart after login to get user's cart
      await fetchCart();
    } catch (err) {
      setError(err.message);
      console.error('Login error:', err);
    } finally {
      setLoading(false);
    }
  };

  const register = async (email, password, firstName, lastName, phone) => {
    try {
      setLoading(true);
      const response = await fetch(`${AUTH_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          email,
          password,
          first_name: firstName,
          last_name: lastName,
          phone: phone || null,
        }),
      });
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.detail || 'Registration failed');
      }
      
      const data = await response.json();
      setAuthToken(data.access_token);
      setUser(data.user);
      localStorage.setItem('authToken', data.access_token);
      setShowAuthModal(false);
      setError(null);
      
      // Refresh cart after registration
      await fetchCart();
    } catch (err) {
      setError(err.message);
      console.error('Registration error:', err);
    } finally {
      setLoading(false);
    }
  };

  const logout = useCallback(async () => {
    try {
      if (authToken) {
        await fetch(`${AUTH_BASE_URL}/auth/logout`, {
          method: 'POST',
          headers: getAuthHeaders(),
        });
      }
    } catch (err) {
      console.error('Logout error:', err);
    } finally {
      setAuthToken(null);
      setUser(null);
      localStorage.removeItem('authToken');
      // Clear cart - will be refetched by useEffect when authToken changes
      setCart([]);
    }
  }, [authToken, getAuthHeaders]);

  const fetchUserProfile = useCallback(async () => {
    if (!authToken) return;
    
    try {
      const response = await fetch(`${AUTH_BASE_URL}/auth/me`, {
        headers: getAuthHeaders(),
      });
      
      if (response.ok) {
        const userData = await response.json();
        setUser(userData);
      } else {
        // Token might be expired, logout
        logout();
      }
    } catch (err) {
      console.error('Error fetching user profile:', err);
      logout();
    }
  }, [authToken, getAuthHeaders, logout]);

  // API Functions
  const fetchProducts = useCallback(async () => {
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE_URL}/products`);
      if (!response.ok) throw new Error('Failed to fetch products');
      const data = await response.json();
      setProducts(data);
    } catch (err) {
      setError('Failed to load products');
      console.error('Error fetching products:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchCart = useCallback(async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/cart?session_id=${sessionId}`, {
        headers: getAuthHeaders(),
      });
      if (!response.ok) throw new Error('Failed to fetch cart');
      const data = await response.json();
      setCart(data);
    } catch (err) {
      setError('Failed to load cart');
      console.error('Error fetching cart:', err);
    }
  }, [sessionId, getAuthHeaders]);

  const addToCartAPI = async (productId, quantity = 1) => {
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE_URL}/cart/${sessionId}/add`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        body: JSON.stringify({
          product_id: productId,
          quantity: quantity
        })
      });
      
      if (!response.ok) throw new Error('Failed to add item to cart');
      await fetchCart();
    } catch (err) {
      setError('Failed to add item to cart');
      console.error('Error adding to cart:', err);
    } finally {
      setLoading(false);
    }
  };

  const removeFromCartAPI = async (itemId) => {
    try {
      const response = await fetch(`${API_BASE_URL}/cart/${sessionId}/item/${itemId}`, {
        method: 'DELETE'
      });
      
      if (!response.ok) throw new Error('Failed to remove item');
      
      await fetchCart(); // Refresh cart
    } catch (err) {
      setError('Failed to remove item from cart');
      console.error('Error removing from cart:', err);
    }
  };

  // Load initial data
  useEffect(() => {
    fetchProducts();
    fetchCart();
    if (authToken) {
      fetchUserProfile();
    }
  }, [fetchProducts, fetchCart, fetchUserProfile, authToken]);

  const addToCart = (product) => {
    addToCartAPI(product.id, 1);
  };

  const removeFromCart = (itemId) => {
    removeFromCartAPI(itemId);
  };

  // Authentication Modal Component
  const AuthModal = () => {
    const [formData, setFormData] = useState({
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      phone: ''
    });

    const handleInputChange = (e) => {
      setFormData({
        ...formData,
        [e.target.name]: e.target.value
      });
    };

    const handleSubmit = async (e) => {
      e.preventDefault();
      if (authMode === 'login') {
        await login(formData.email, formData.password);
      } else {
        await register(
          formData.email,
          formData.password,
          formData.firstName,
          formData.lastName,
          formData.phone
        );
      }
    };

    if (!showAuthModal) return null;

    return (
      <div className="modal-overlay" onClick={() => setShowAuthModal(false)}>
        <div className="modal-content" onClick={(e) => e.stopPropagation()}>
          <div className="modal-header">
            <h2>{authMode === 'login' ? 'Login' : 'Create Account'}</h2>
            <button className="close-btn" onClick={() => setShowAuthModal(false)}>×</button>
          </div>
          
          <form onSubmit={handleSubmit} className="auth-form">
            {authMode === 'register' && (
              <>
                <div className="form-row">
                  <input
                    type="text"
                    name="firstName"
                    placeholder="First Name"
                    value={formData.firstName}
                    onChange={handleInputChange}
                    required
                  />
                  <input
                    type="text"
                    name="lastName"
                    placeholder="Last Name"
                    value={formData.lastName}
                    onChange={handleInputChange}
                    required
                  />
                </div>
                <input
                  type="tel"
                  name="phone"
                  placeholder="Phone (optional)"
                  value={formData.phone}
                  onChange={handleInputChange}
                />
              </>
            )}
            
            <input
              type="email"
              name="email"
              placeholder="Email"
              value={formData.email}
              onChange={handleInputChange}
              required
            />
            
            <input
              type="password"
              name="password"
              placeholder="Password"
              value={formData.password}
              onChange={handleInputChange}
              required
              minLength="6"
            />
            
            {error && <div className="error-message">{error}</div>}
            
            <button type="submit" className="auth-submit-btn" disabled={loading}>
              {loading ? 'Please wait...' : (authMode === 'login' ? 'Login' : 'Create Account')}
            </button>
          </form>
          
          <div className="auth-switch">
            {authMode === 'login' ? (
              <p>
                Don't have an account?{' '}
                <button 
                  type="button" 
                  className="link-btn"
                  onClick={() => setAuthMode('register')}
                >
                  Sign up
                </button>
              </p>
            ) : (
              <p>
                Already have an account?{' '}
                <button 
                  type="button" 
                  className="link-btn"
                  onClick={() => setAuthMode('login')}
                >
                  Login
                </button>
              </p>
            )}
          </div>
        </div>
      </div>
    );
  };

  // Navigation Component
  const Navigation = () => (
    <nav className="navbar">
      <div className="nav-brand" onClick={() => setCurrentPage('home')}>
        <h1>✨ Luxe Jewelry</h1>
      </div>
      <div className="nav-links">
        <button 
          className={currentPage === 'home' ? 'active' : ''}
          onClick={() => setCurrentPage('home')}
        >
          Home
        </button>
        <button 
          className={currentPage === 'products' ? 'active' : ''}
          onClick={() => setCurrentPage('products')}
        >
          Products
        </button>
        <button 
          className={currentPage === 'cart' ? 'active' : ''}
          onClick={() => setCurrentPage('cart')}
        >
          Cart ({cart.length})
        </button>
        
        {user ? (
          <div className="user-menu">
            <span className="user-greeting">Hi, {user.first_name}!</span>
            <button className="logout-btn" onClick={logout}>
              Logout
            </button>
          </div>
        ) : (
          <button 
            className="login-btn"
            onClick={() => {
              setAuthMode('login');
              setShowAuthModal(true);
              setError(null);
            }}
          >
            Login
          </button>
        )}
      </div>
    </nav>
  );

  const renderHome = () => (
    <div className="home">
      <section className="hero">
        <div className="hero-content">
          <h1>Exquisite Jewelry Collection</h1>
          <p>Discover timeless elegance with our handcrafted jewelry pieces</p>
          <button className="cta-button" onClick={() => setCurrentPage('products')}>
            Shop Now
          </button>
        </div>
      </section>
      
      <section className="featured-products">
        <h2>Featured Products</h2>
        <div className="products-grid">
          {products.slice(0, 3).map(product => (
            <div key={product.id} className="product-card">
              <img src={product.image} alt={product.name} />
              <h3>{product.name}</h3>
              <p className="price">${product.price}</p>
              <button onClick={() => addToCart(product)} className="add-to-cart-btn">
                Add to Cart
              </button>
            </div>
          ))}
        </div>
      </section>
    </div>
  );

  const renderProducts = () => (
    <div className="products-page">
      <h2>Our Collection</h2>
      <div className="products-grid">
        {products.map(product => (
          <div key={product.id} className="product-card">
            <img src={product.image} alt={product.name} />
            <h3>{product.name}</h3>
            <p className="description">{product.description}</p>
            <p className="price">${product.price}</p>
            <button onClick={() => addToCart(product)} className="add-to-cart-btn">
              Add to Cart
            </button>
          </div>
        ))}
      </div>
    </div>
  );

  const renderCart = () => {
    // Calculate cart total from cart items
    const cartTotal = cart.reduce((total, item) => {
      const product = products.find(p => p.id === item.product_id);
      return total + (product ? product.price * item.quantity : 0);
    }, 0);

    return (
      <div className="cart-page">
        <h2>Shopping Cart</h2>
        {error && <div className="error-message" style={{color: 'red', textAlign: 'center', margin: '1rem 0'}}>{error}</div>}
        {loading && <div className="loading-message" style={{textAlign: 'center', margin: '2rem 0'}}>Loading...</div>}
        {cart.length === 0 ? (
          <p className="empty-cart">Your cart is empty</p>
        ) : (
          <div>
            <div className="cart-items">
              {cart.map(item => {
                const product = products.find(p => p.id === item.product_id);
                if (!product) return null;
                const itemTotal = product.price * item.quantity;
                
                return (
                  <div key={item.id} className="cart-item">
                    <img src={product.image} alt={product.name} />
                    <div className="item-details">
                      <h3>{product.name}</h3>
                      <p>Quantity: {item.quantity}</p>
                      <p className="price">${itemTotal.toFixed(2)}</p>
                    </div>
                    <button onClick={() => removeFromCart(item.id)} className="remove-btn">
                      Remove
                    </button>
                  </div>
                );
              })}
            </div>
            <div className="cart-total">
              <h3>Total: ${cartTotal.toFixed(2)}</h3>
              <button className="checkout-btn">Proceed to Checkout</button>
            </div>
          </div>
        )}
      </div>
    );
  };

  const renderCurrentPage = () => {
    switch(currentPage) {
      case 'home':
        return renderHome();
      case 'products':
        return renderProducts();
      case 'cart':
        return renderCart();
      default:
        return renderHome();
    }
  };

  return (
    <div className="App">
      <Navigation />
      <AuthModal />
      <main className="main-content">
        {renderCurrentPage()}
      </main>
      <footer className="footer">
        <p>&copy; 2024 Luxe Jewelry. All rights reserved.</p>
      </footer>
    </div>
  );
}

export default App;
