# Backend Pipeline Shared Library

This directory contains shared code for the backend CI/CD pipeline to reduce duplication and improve maintainability.

## 📁 **Structure**

```
jenkins/shared/
├── BackendPipeline.groovy    # Main pipeline functions
├── BackendConfig.groovy      # Configuration constants
└── README.md                 # This documentation
```

## 🚀 **Usage**

### **Option 1: Direct Import (Current)**
```groovy
// In your Jenkinsfile
load 'jenkins/shared/BackendPipeline.groovy'

pipeline {
    stages {
        stage('Version Info') {
            when(BackendPipeline.getCommonWhenConditions())
            steps {
                script {
                    def imageTag = BackendPipeline.generateImageTag(...)
                }
            }
        }
    }
}
```

### **Option 2: Jenkins Shared Library (Recommended)**
1. Set up Jenkins Shared Library in Jenkins configuration
2. Use `@Library('shared-pipeline-library') _` at the top of Jenkinsfile
3. Reference functions directly: `BackendPipeline.setupPythonEnvironment()`

## 🔧 **Available Functions**

### **When Conditions**
- `getCommonWhenConditions()` - Standard conditions for most stages
- `getEcrPushWhenConditions()` - Excludes PRs from ECR push
- `getGitTagWhenConditions()` - Only production and release branches

### **Version Management**
- `generateImageTag(branchName, latestTag, buildNumber, commitSha)` - Smart tag generation
- `getLatestBackendTag()` - Get latest backend version from Git tags

### **Build Steps**
- `setupPythonEnvironment()` - Install Python dependencies
- `runLinting()` - Code formatting and style checks
- `runTests()` - Run tests with coverage
- `runSecurityScans()` - Security vulnerability scanning
- `buildDockerImage(imageTag, registry, repository)` - Build and tag Docker image
- `pushToEcr(imageTag, registry, repository, tags, region)` - Push to AWS ECR
- `createGitTag(imageTag)` - Create and push Git tags

### **Configuration**
- `getEnvironmentVariables()` - Standard environment variables
- `getPodTemplate()` - Kubernetes pod template for Jenkins agents

## 📋 **Configuration**

All configuration is centralized in `BackendConfig.groovy`:

```groovy
// AWS Settings
BackendConfig.AWS_REGION           // us-east-2
BackendConfig.ECR_REGISTRY         // ECR registry URL
BackendConfig.ECR_REPOSITORY       // jewleryapp/be

// Resource Limits
BackendConfig.RESOURCE_LIMITS      // Memory/CPU limits for containers

// Tool Versions
BackendConfig.TOOL_VERSIONS        // Versions for linting/testing tools

// Branch Patterns
BackendConfig.PRODUCTION_BRANCHES  // ['main', 'master']
BackendConfig.BACKEND_BRANCHES     // Backend-specific patterns
```

## 🎯 **Benefits**

### **Before (Original Jenkinsfile)**
- ❌ **544 lines** of duplicated code
- ❌ **Hard to maintain** - changes needed in multiple places
- ❌ **Error-prone** - easy to miss updates
- ❌ **No reusability** across services

### **After (Shared Library)**
- ✅ **~100 lines** in simplified Jenkinsfile
- ✅ **Easy maintenance** - central updates
- ✅ **Consistent behavior** across pipelines
- ✅ **Reusable** for other services (auth-service, frontend)

## 🔄 **Migration Guide**

### **Step 1: Test Shared Version**
```bash
# Use the new shared Jenkinsfile
cp backend/Jenkinsfile.shared backend/Jenkinsfile
git add backend/Jenkinsfile
git commit -m "migrate: use shared pipeline library"
```

### **Step 2: Set Up Jenkins Library (Optional)**
1. In Jenkins: Manage Jenkins → Configure System → Global Pipeline Libraries
2. Add library with name `shared-pipeline-library`
3. Point to this repository's `jenkins/shared/` directory
4. Update Jenkinsfile to use `@Library` syntax

### **Step 3: Extend for Other Services**
```groovy
// Create AuthServicePipeline.groovy, FrontendPipeline.groovy
// Reuse common functions, customize service-specific logic
```

## 📊 **Supported Branch Patterns**

| Branch Pattern | Image Tag Example | ECR Push | Git Tag |
|----------------|-------------------|----------|---------|
| `main` | `1.1.0` | ✅ | ✅ |
| `PR-123` | `1.1.0-pr-123.1.abc1234` | ❌ | ❌ |
| `feature/backend/1.2.0/auth` | `1.2.0-auth.1.abc1234` | ✅ | ❌ |
| `backend/release/1.1.1` | `1.1.1-rc.1` | ✅ | ✅ |
| `patch/backend/1.1.1/fix` | `1.1.1-fix.1.abc1234` | ✅ | ❌ |

## 🛠 **Customization**

### **Add New Stage**
```groovy
// In BackendPipeline.groovy
static def runCustomStep() {
    sh '''
        echo "Custom step for backend"
        # Your custom logic here
    '''
}

// In Jenkinsfile
stage('Custom Step') {
    when(BackendPipeline.getCommonWhenConditions())
    steps {
        container('python') {
            dir('backend') {
                script {
                    BackendPipeline.runCustomStep()
                }
            }
        }
    }
}
```

### **Modify Configuration**
```groovy
// In BackendConfig.groovy
static final String CUSTOM_SETTING = 'value'

// Use in pipeline
def setting = BackendConfig.CUSTOM_SETTING
```

## 🚀 **Next Steps**

1. **Test the shared version** with current pipeline
2. **Set up Jenkins Shared Library** for cleaner syntax
3. **Create similar libraries** for auth-service and frontend
4. **Add monitoring and notifications** to shared functions
5. **Implement deployment stages** for different environments

This shared library makes your pipeline more maintainable, reusable, and easier to extend across your monorepo! 🎉
