import { render, screen, waitFor } from '@testing-library/react';
import App from './App';

// Mock fetch globally
global.fetch = jest.fn();

beforeEach(() => {
  // Clear all mocks before each test
  jest.clearAllMocks();
  
  // Mock localStorage
  Storage.prototype.getItem = jest.fn(() => null);
  Storage.prototype.setItem = jest.fn();
  
  // Mock fetch responses
  global.fetch.mockImplementation((url) => {
    if (url.includes('/products')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve([
          { id: 1, name: 'Diamond Ring', price: 1299.99, description: 'Beautiful diamond ring', image: 'ring.jpg' },
          { id: 2, name: 'Gold Necklace', price: 899.99, description: 'Elegant gold necklace', image: 'necklace.jpg' }
        ])
      });
    }
    if (url.includes('/cart')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve([])
      });
    }
    return Promise.resolve({
      ok: true,
      json: () => Promise.resolve({})
    });
  });
});

test('renders Luxe Jewelry app and validates it is running', async () => {
  render(<App />);
  
  // Check that the main navigation is rendered
  const brandElement = screen.getByText(/Luxe Jewelry/i);
  expect(brandElement).toBeInTheDocument();
  
  // Check that navigation links are present
  expect(screen.getByText('Home')).toBeInTheDocument();
  expect(screen.getByText('Products')).toBeInTheDocument();
  expect(screen.getByText(/Cart/i)).toBeInTheDocument();
  
  // Wait for products to load
  await waitFor(() => {
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/products'));
  });
});

test('renders hero section on home page', () => {
  render(<App />);
  
  // Check hero section content
  const heroTitle = screen.getByText(/Exquisite Jewelry Collection/i);
  expect(heroTitle).toBeInTheDocument();
  
  const heroDescription = screen.getByText(/Discover timeless elegance/i);
  expect(heroDescription).toBeInTheDocument();
  
  const shopButton = screen.getByText(/Shop Now/i);
  expect(shopButton).toBeInTheDocument();
});

test('renders footer', () => {
  render(<App />);
  
  const footer = screen.getByText(/© 2024 Luxe Jewelry. All rights reserved./i);
  expect(footer).toBeInTheDocument();
});

test('app fetches products on mount', async () => {
  render(<App />);
  
  await waitFor(() => {
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/products'));
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('/cart'));
  });
});
