import os
import uuid
from datetime import datetime, timedelta
from typing import List, Optional

import bcrypt
import jwt
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, EmailStr, Field

tags_metadata = [
    {
        "name": "Authentication",
        "description": "User registration, login, logout, and token management.",
    },
    {
        "name": "User Profile",
        "description": "View and update user profile information.",
    },
    {
        "name": "Admin",
        "description": "Administrative endpoints for user management.",
    },
    {
        "name": "Health",
        "description": "Health check and readiness endpoints for monitoring.",
    },
]

app = FastAPI(
    title="Luxe Jewelry Store - Auth Service",
    description="""
## Authentication Service for Luxe Jewelry Store

Handles all user authentication and authorization:

* **Registration** - Create new user accounts with secure password hashing
* **Login/Logout** - JWT-based authentication with configurable expiration
* **Profile Management** - Update user information and change passwords

### Security
- Passwords are hashed using bcrypt
- JWT tokens expire after 30 minutes
- Bearer token authentication required for protected endpoints
    """,
    version="1.0.0",
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

# JWT Configuration
SECRET_KEY = os.getenv("JWT_SECRET_KEY", "your-secret-key-change-in-production")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

security = HTTPBearer()


# Data Models
class UserCreate(BaseModel):
    email: EmailStr = Field(..., example="john.doe@example.com", description="User's email address")
    password: str = Field(..., example="SecurePass123!", min_length=8, description="Password (min 8 characters)")
    first_name: str = Field(..., example="John", description="User's first name")
    last_name: str = Field(..., example="Doe", description="User's last name")
    phone: Optional[str] = Field(None, example="+1-555-123-4567", description="Phone number (optional)")

    class Config:
        json_schema_extra = {
            "example": {
                "email": "john.doe@example.com",
                "password": "SecurePass123!",
                "first_name": "John",
                "last_name": "Doe",
                "phone": "+1-555-123-4567"
            }
        }


class UserLogin(BaseModel):
    email: EmailStr = Field(..., example="john.doe@example.com", description="Registered email address")
    password: str = Field(..., example="SecurePass123!", description="Account password")

    class Config:
        json_schema_extra = {
            "example": {
                "email": "john.doe@example.com",
                "password": "SecurePass123!"
            }
        }


class UserResponse(BaseModel):
    id: str = Field(..., example="550e8400-e29b-41d4-a716-446655440000", description="Unique user identifier")
    email: str = Field(..., example="john.doe@example.com", description="User's email address")
    first_name: str = Field(..., example="John", description="User's first name")
    last_name: str = Field(..., example="Doe", description="User's last name")
    phone: Optional[str] = Field(None, example="+1-555-123-4567", description="Phone number")
    created_at: datetime = Field(..., description="Account creation timestamp")
    is_active: bool = Field(..., example=True, description="Account active status")

    class Config:
        json_schema_extra = {
            "example": {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "email": "john.doe@example.com",
                "first_name": "John",
                "last_name": "Doe",
                "phone": "+1-555-123-4567",
                "created_at": "2024-01-15T10:30:00",
                "is_active": True
            }
        }


class UserUpdate(BaseModel):
    first_name: Optional[str] = Field(None, example="John", description="New first name")
    last_name: Optional[str] = Field(None, example="Doe", description="New last name")
    phone: Optional[str] = Field(None, example="+1-555-123-4567", description="New phone number")

    class Config:
        json_schema_extra = {
            "example": {
                "first_name": "John",
                "last_name": "Smith",
                "phone": "+1-555-987-6543"
            }
        }


class TokenResponse(BaseModel):
    access_token: str = Field(..., example="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", description="JWT access token")
    token_type: str = Field(..., example="bearer", description="Token type")
    expires_in: int = Field(..., example=1800, description="Token expiration time in seconds")
    user: UserResponse = Field(..., description="User profile information")

    class Config:
        json_schema_extra = {
            "example": {
                "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJleHAiOjE3MDUzMTg2MDB9.abc123",
                "token_type": "bearer",
                "expires_in": 1800,
                "user": {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "email": "john.doe@example.com",
                    "first_name": "John",
                    "last_name": "Doe",
                    "phone": "+1-555-123-4567",
                    "created_at": "2024-01-15T10:30:00",
                    "is_active": True
                }
            }
        }


class PasswordChange(BaseModel):
    current_password: str = Field(..., example="OldPass123!", description="Current password")
    new_password: str = Field(..., example="NewSecurePass456!", min_length=8, description="New password (min 8 characters)")

    class Config:
        json_schema_extra = {
            "example": {
                "current_password": "OldPass123!",
                "new_password": "NewSecurePass456!"
            }
        }


# In-memory user storage (in production, use a database)
users_db = {}


# Utility Functions
def hash_password(password: str) -> str:
    """Hash a password using bcrypt"""
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")


def verify_password(password: str, hashed: str) -> bool:
    """Verify a password against its hash"""
    return bcrypt.checkpw(password.encode("utf-8"), hashed.encode("utf-8"))


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    """Create a JWT access token"""
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt


def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """Verify JWT token and return user data"""
    try:
        payload = jwt.decode(
            credentials.credentials, SECRET_KEY, algorithms=[ALGORITHM]
        )
        user_id: str = payload.get("sub")
        if user_id is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid authentication credentials",
                headers={"WWW-Authenticate": "Bearer"},
            )
        return user_id
    except jwt.PyJWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )


def get_current_user(user_id: str = Depends(verify_token)):
    """Get current user from token"""
    user = users_db.get(user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found"
        )
    return user


# API Endpoints


@app.get(
    "/",
    tags=["Health"],
    summary="API Root",
    response_description="Welcome message",
)
async def root():
    """
    Root endpoint returning API welcome message.
    """
    return {"message": "Welcome to Luxe Jewelry Store Auth Service"}


@app.post(
    "/auth/register",
    response_model=TokenResponse,
    tags=["Authentication"],
    summary="Register New User",
    response_description="Registration successful with access token",
    responses={
        200: {"description": "User registered successfully"},
        400: {
            "description": "Email already registered",
            "content": {
                "application/json": {
                    "example": {"detail": "Email already registered"}
                }
            }
        }
    }
)
async def register_user(user_data: UserCreate):
    """
    Register a new user account.
    
    - **email**: Valid email address (must be unique)
    - **password**: Minimum 8 characters
    - **first_name**: User's first name
    - **last_name**: User's last name
    - **phone**: Optional phone number
    
    Returns a JWT access token upon successful registration.
    """
    # Check if user already exists
    for user in users_db.values():
        if user["email"] == user_data.email:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Email already registered",
            )

    # Create new user
    user_id = str(uuid.uuid4())
    hashed_password = hash_password(user_data.password)

    new_user = {
        "id": user_id,
        "email": user_data.email,
        "password": hashed_password,
        "first_name": user_data.first_name,
        "last_name": user_data.last_name,
        "phone": user_data.phone,
        "created_at": datetime.utcnow(),
        "is_active": True,
    }

    users_db[user_id] = new_user

    # Create access token
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user_id}, expires_delta=access_token_expires
    )

    # Prepare user response (without password)
    user_response = UserResponse(
        id=new_user["id"],
        email=new_user["email"],
        first_name=new_user["first_name"],
        last_name=new_user["last_name"],
        phone=new_user["phone"],
        created_at=new_user["created_at"],
        is_active=new_user["is_active"],
    )

    return TokenResponse(
        access_token=access_token,
        token_type="bearer",
        expires_in=ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        user=user_response,
    )


@app.post(
    "/auth/login",
    response_model=TokenResponse,
    tags=["Authentication"],
    summary="User Login",
    response_description="Login successful with access token",
    responses={
        200: {"description": "Login successful"},
        400: {
            "description": "Inactive user",
            "content": {
                "application/json": {
                    "example": {"detail": "Inactive user"}
                }
            }
        },
        401: {
            "description": "Invalid credentials",
            "content": {
                "application/json": {
                    "example": {"detail": "Incorrect email or password"}
                }
            }
        }
    }
)
async def login_user(login_data: UserLogin):
    """
    Authenticate user and obtain access token.
    
    - **email**: Registered email address
    - **password**: Account password
    
    Returns a JWT access token valid for 30 minutes.
    """
    # Find user by email
    user = None
    for u in users_db.values():
        if u["email"] == login_data.email:
            user = u
            break

    if not user or not verify_password(login_data.password, user["password"]):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
        )

    if not user["is_active"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="Inactive user"
        )

    # Create access token
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user["id"]}, expires_delta=access_token_expires
    )

    # Prepare user response (without password)
    user_response = UserResponse(
        id=user["id"],
        email=user["email"],
        first_name=user["first_name"],
        last_name=user["last_name"],
        phone=user["phone"],
        created_at=user["created_at"],
        is_active=user["is_active"],
    )

    return TokenResponse(
        access_token=access_token,
        token_type="bearer",
        expires_in=ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        user=user_response,
    )


@app.get(
    "/auth/me",
    response_model=UserResponse,
    tags=["User Profile"],
    summary="Get Current User Profile",
    response_description="Current user's profile information",
    responses={
        200: {"description": "Profile retrieved successfully"},
        401: {
            "description": "Not authenticated",
            "content": {
                "application/json": {
                    "example": {"detail": "Invalid authentication credentials"}
                }
            }
        }
    }
)
async def get_current_user_profile(current_user: dict = Depends(get_current_user)):
    """
    Get the authenticated user's profile information.
    
    Requires a valid JWT Bearer token in the Authorization header.
    """
    return UserResponse(
        id=current_user["id"],
        email=current_user["email"],
        first_name=current_user["first_name"],
        last_name=current_user["last_name"],
        phone=current_user["phone"],
        created_at=current_user["created_at"],
        is_active=current_user["is_active"],
    )


@app.put(
    "/auth/me",
    response_model=UserResponse,
    tags=["User Profile"],
    summary="Update User Profile",
    response_description="Updated user profile",
    responses={
        200: {"description": "Profile updated successfully"},
        401: {
            "description": "Not authenticated",
            "content": {
                "application/json": {
                    "example": {"detail": "Invalid authentication credentials"}
                }
            }
        }
    }
)
async def update_user_profile(
    user_update: UserUpdate, current_user: dict = Depends(get_current_user)
):
    """
    Update the authenticated user's profile.
    
    All fields are optional - only provided fields will be updated.
    
    - **first_name**: New first name
    - **last_name**: New last name
    - **phone**: New phone number
    """
    user_id = current_user["id"]

    # Update user data
    if user_update.first_name is not None:
        users_db[user_id]["first_name"] = user_update.first_name
    if user_update.last_name is not None:
        users_db[user_id]["last_name"] = user_update.last_name
    if user_update.phone is not None:
        users_db[user_id]["phone"] = user_update.phone

    updated_user = users_db[user_id]

    return UserResponse(
        id=updated_user["id"],
        email=updated_user["email"],
        first_name=updated_user["first_name"],
        last_name=updated_user["last_name"],
        phone=updated_user["phone"],
        created_at=updated_user["created_at"],
        is_active=updated_user["is_active"],
    )


@app.post(
    "/auth/change-password",
    tags=["User Profile"],
    summary="Change Password",
    response_description="Password change confirmation",
    responses={
        200: {
            "description": "Password changed successfully",
            "content": {
                "application/json": {
                    "example": {"message": "Password changed successfully"}
                }
            }
        },
        400: {
            "description": "Incorrect current password",
            "content": {
                "application/json": {
                    "example": {"detail": "Incorrect current password"}
                }
            }
        },
        401: {
            "description": "Not authenticated",
            "content": {
                "application/json": {
                    "example": {"detail": "Invalid authentication credentials"}
                }
            }
        }
    }
)
async def change_password(
    password_data: PasswordChange, current_user: dict = Depends(get_current_user)
):
    """
    Change the authenticated user's password.
    
    - **current_password**: Must match the existing password
    - **new_password**: New password (minimum 8 characters)
    """
    user_id = current_user["id"]

    # Verify current password
    if not verify_password(password_data.current_password, current_user["password"]):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="Incorrect current password"
        )

    # Update password
    new_hashed_password = hash_password(password_data.new_password)
    users_db[user_id]["password"] = new_hashed_password

    return {"message": "Password changed successfully"}


@app.post(
    "/auth/logout",
    tags=["Authentication"],
    summary="User Logout",
    response_description="Logout confirmation",
    responses={
        200: {
            "description": "Logged out successfully",
            "content": {
                "application/json": {
                    "example": {"message": "Logged out successfully"}
                }
            }
        },
        401: {
            "description": "Not authenticated",
            "content": {
                "application/json": {
                    "example": {"detail": "Invalid authentication credentials"}
                }
            }
        }
    }
)
async def logout_user(current_user: dict = Depends(get_current_user)):
    """
    Logout the current user.
    
    In a production environment, this would blacklist the token.
    """
    return {"message": "Logged out successfully"}


@app.get(
    "/auth/users",
    response_model=List[UserResponse],
    tags=["Admin"],
    summary="List All Users",
    response_description="List of all registered users",
    responses={
        200: {
            "description": "Users retrieved successfully",
            "content": {
                "application/json": {
                    "example": [
                        {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "email": "john.doe@example.com",
                            "first_name": "John",
                            "last_name": "Doe",
                            "phone": "+1-555-123-4567",
                            "created_at": "2024-01-15T10:30:00",
                            "is_active": True
                        }
                    ]
                }
            }
        }
    }
)
async def get_all_users():
    """
    Get all registered users.
    
    **Note**: This is an admin endpoint. In production, add admin authentication.
    """
    users = []
    for user in users_db.values():
        users.append(
            UserResponse(
                id=user["id"],
                email=user["email"],
                first_name=user["first_name"],
                last_name=user["last_name"],
                phone=user["phone"],
                created_at=user["created_at"],
                is_active=user["is_active"],
            )
        )
    return users


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
                        "service": "auth-service",
                        "timestamp": "2024-01-15T10:30:00",
                        "users_count": 5
                    }
                }
            }
        }
    }
)
async def health_check():
    """
    Health check endpoint for monitoring and CI/CD.
    
    Returns service status and user count.
    """
    return {
        "status": "healthy",
        "service": "auth-service",
        "timestamp": datetime.utcnow(),
        "users_count": len(users_db),
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
                        "service": "auth-service",
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
    return {
        "status": "ready",
        "service": "auth-service",
        "timestamp": datetime.utcnow(),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
