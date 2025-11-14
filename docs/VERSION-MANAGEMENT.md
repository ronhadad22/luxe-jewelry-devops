# Version Management Guide - Monorepo Best Practices

## 🏷️ Git Tag-Based Versioning Strategy

This project uses **service-specific Git tags** for version management in our monorepo.

### Tag Format

```bash
# Service-specific tags
backend/v1.2.3
auth-service/v1.0.1
frontend/v2.1.0

# Global release tags (optional)
v1.2.0  # Major coordinated release
```

## 🚀 How It Works

### 1. Automatic Version Generation

The Jenkins pipeline automatically generates versions based on:
- **Git tags**: Latest service-specific tag
- **Branch context**: Different strategies per branch
- **Commit info**: SHA for traceability

### 2. Branch-Specific Versioning

| Branch Type | Version Format | Example | Git Tag Created |
|-------------|----------------|---------|-----------------|
| `main/master` | `MAJOR.MINOR.PATCH` | `1.2.3` | ✅ `backend/v1.2.3` |
| `develop` | `VERSION-dev.BUILD.SHA` | `1.2.3-dev.45.abc123` | ❌ |
| `release/X.Y.Z` | `X.Y.Z-rc.BUILD` | `1.3.0-rc.12` | ✅ `backend/v1.3.0-rc.12` |
| `feature/name` | `VERSION-name.BUILD.SHA` | `1.2.3-auth-fix.23.def456` | ❌ |
| `hotfix/name` | `VERSION-hotfix.BUILD.SHA` | `1.2.4-hotfix.8.ghi789` | ❌ |

### 3. Docker Image Tags

Each build creates multiple Docker tags for flexibility:

```bash
# Primary version tag
950555670656.dkr.ecr.us-east-2.amazonaws.com/jewleryapp/be:1.2.3

# Additional tags
950555670656.dkr.ecr.us-east-2.amazonaws.com/jewleryapp/be:latest
950555670656.dkr.ecr.us-east-2.amazonaws.com/jewleryapp/be:stable
950555670656.dkr.ecr.us-east-2.amazonaws.com/jewleryapp/be:commit-abc123
950555670656.dkr.ecr.us-east-2.amazonaws.com/jewleryapp/be:build-45
```

## 📋 Release Process

### 1. Development Flow

```bash
# Work on feature branch
git checkout -b feature/user-authentication
# ... make changes ...
git commit -m "feat: add user authentication endpoint"
git push origin feature/user-authentication

# Jenkins builds: 1.2.3-user-authentication.45.abc123
```

### 2. Release Preparation

```bash
# Create release branch
git checkout -b release/1.3.0
git push origin release/1.3.0

# Jenkins builds: 1.3.0-rc.12
# Test the release candidate
```

### 3. Production Release

```bash
# Merge to main
git checkout main
git merge release/1.3.0
git push origin main

# Jenkins automatically:
# 1. Builds: 1.3.0
# 2. Creates Git tag: backend/v1.3.0
# 3. Pushes multiple Docker tags
```

### 4. Hotfix Process

```bash
# Create hotfix from main
git checkout -b hotfix/security-fix
# ... fix critical issue ...
git commit -m "fix: resolve security vulnerability"
git push origin hotfix/security-fix

# Jenkins builds: 1.3.1-hotfix.8.def456

# Merge to main
git checkout main
git merge hotfix/security-fix
git push origin main

# Jenkins creates: backend/v1.3.1
```

## 🔍 Version Lookup

### Find Latest Version

```bash
# Latest backend version
git tag --list --sort=-version:refname "backend/v*" | head -n1

# Latest auth service version
git tag --list --sort=-version:refname "auth-service/v*" | head -n1

# All service versions
git tag --list --sort=-version:refname "**/v*"
```

### Check What Changed

```bash
# Changes since last backend release
git log backend/v1.2.2..backend/v1.2.3 --oneline

# Changes between any two versions
git log backend/v1.2.0..backend/v1.3.0 --oneline
```

## 🛠️ Manual Version Management

### Create a Release Tag

```bash
# For production release
git tag -a "backend/v1.3.0" -m "Release backend version 1.3.0"
git push origin "backend/v1.3.0"

# For release candidate
git tag -a "backend/v1.3.0-rc.1" -m "Release candidate backend v1.3.0-rc.1"
git push origin "backend/v1.3.0-rc.1"
```

### Deploy Specific Version

```bash
# Using Helm with specific image tag
helm upgrade my-app ./helm/luxe-jewelry-app \
  --set backend.image.tag=1.2.3 \
  --set frontend.image.tag=2.1.0

# Using latest stable
helm upgrade my-app ./helm/luxe-jewelry-app \
  --set backend.image.tag=stable
```

## 📊 Version Strategy Benefits

### ✅ Advantages

- **Traceability**: Every version maps to a Git commit
- **Rollback**: Easy to identify and rollback to specific versions
- **Parallel Development**: Services can evolve independently
- **Clear History**: Git tags provide release timeline
- **Automation**: No manual version bumping needed

### 🎯 Best Practices

1. **Semantic Versioning**: Follow MAJOR.MINOR.PATCH
2. **Meaningful Commits**: Use conventional commit messages
3. **Release Branches**: Use for coordinated releases
4. **Tag Protection**: Protect release tags from deletion
5. **Documentation**: Update CHANGELOG for major releases

## 🔧 Troubleshooting

### No Tags Found

```bash
# If no tags exist, pipeline defaults to 1.0.0
# Create initial tag:
git tag -a "backend/v1.0.0" -m "Initial backend release"
git push origin "backend/v1.0.0"
```

### Wrong Version Generated

```bash
# Check latest tag
git tag --list --sort=-version:refname "backend/v*" | head -n1

# Verify tag format (must be: service/vX.Y.Z)
git tag --list "backend/v*"
```

### Rollback Release

```bash
# Find previous version
git tag --list --sort=-version:refname "backend/v*" | head -n2

# Deploy previous version
helm upgrade my-app ./helm/luxe-jewelry-app \
  --set backend.image.tag=1.2.2
```

This approach gives you the benefits of Git-based version management while handling the complexities of a monorepo with multiple services.
