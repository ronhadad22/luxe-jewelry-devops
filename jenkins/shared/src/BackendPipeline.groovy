/**
 * Shared Backend Pipeline Library
 * Contains common functions and configurations for backend CI/CD
 */

class BackendPipeline {
    
    /**
     * Common when conditions for backend stages
     */
    static def getCommonWhenConditions() {
        return {
            anyOf {
                changeset "backend/**"
                triggeredBy cause: "UserIdCause"
                anyOf {
                    branch 'main'
                    branch 'master'
                }
                branch 'backend/PR-*'
                branch 'PR-*'
                branch 'backend/release/*'
                branch 'feature/backend/*'
                branch 'patch/backend/*'
                changelog ''
            }
        }
    }
    
    /**
     * When conditions for ECR push (excludes PRs)
     */
    static def getEcrPushWhenConditions() {
        return {
            anyOf {
                changeset "backend/**"
                triggeredBy cause: "UserIdCause"
                anyOf {
                    branch 'main'
                    branch 'master'
                }
                branch 'backend/release/*'
                branch 'feature/backend/*'
                branch 'patch/backend/*'
            }
            not {
                anyOf {
                    branch 'PR-*'
                    branch 'backend/PR-*'
                }
            }
        }
    }
    
    /**
     * When conditions for Git tag creation
     */
    static def getGitTagWhenConditions() {
        return {
            anyOf {
                anyOf {
                    branch 'main'
                    branch 'master'
                }
                branch 'backend/release/*'
                branch 'release/*'
            }
        }
    }
    
    /**
     * Generate image tag based on branch name and context
     */
    static def generateImageTag(branchName, latestTag, buildNumber, commitSha) {
        def imageTag = ''
        
        switch (branchName) {
            case 'main':
            case 'master':
                imageTag = latestTag
                break
            case 'develop':
            case 'development':
                imageTag = "${latestTag}-dev.${buildNumber}.${commitSha}"
                break
            case 'be/release':
            case 'backend/release':
                // Backend release branch - increment patch version for new release
                def versionParts = latestTag.split('\\.')
                def patch = (versionParts[2] as Integer) + 1
                imageTag = "${versionParts[0]}.${versionParts[1]}.${patch}"
                break
            case ~/^backend\/release\/.*/:
                def releaseVersion = branchName.replaceAll('backend/release/', '')
                imageTag = "${releaseVersion}-rc.${buildNumber}"
                break
            case ~/^release\/.*/:
                def releaseVersion = branchName.replaceAll('release/', '')
                imageTag = "${releaseVersion}"
                break
            case ~/^feature\/backend\/.*/:
                def featureParts = branchName.split('/')
                def featureName = featureParts.length > 3 ? featureParts[3] : 'feature'
                def targetVersion = featureParts.length > 2 ? featureParts[2] : latestTag
                imageTag = "${targetVersion}-${featureName}.${buildNumber}.${commitSha}"
                break
            case ~/^patch\/backend\/.*/:
                def patchParts = branchName.split('/')
                def patchName = patchParts.length > 3 ? patchParts[3] : 'patch'
                def targetVersion = patchParts.length > 2 ? patchParts[2] : latestTag
                imageTag = "${targetVersion}-${patchName}.${buildNumber}.${commitSha}"
                break
            case ~/^PR-.*/:
                def prNumber = branchName.replaceAll('PR-', '')
                imageTag = "${latestTag}-pr-${prNumber}.${buildNumber}.${commitSha}"
                break
            case ~/^feature\/.*/:
                def featureName = branchName.replaceAll('feature/', '').replaceAll('[^a-zA-Z0-9]', '-').toLowerCase()
                imageTag = "${latestTag}-${featureName}.${buildNumber}.${commitSha}"
                break
            default:
                def cleanBranch = branchName.replaceAll('[^a-zA-Z0-9]', '-').toLowerCase()
                imageTag = "${latestTag}-${cleanBranch}.${buildNumber}.${commitSha}"
                break
        }
        
        return imageTag
    }
    
    /**
     * Get latest backend version tag
     */
    static def getLatestBackendTag() {
        def latestTag = sh(
            script: "git tag --list --sort=-version:refname 'backend/v*' | head -n1",
            returnStdout: true
        ).trim()
        
        if (latestTag) {
            return latestTag.replaceAll('backend/v', '')
        }
        
        // Fallback to global tags if no service-specific tags
        def globalTag = sh(
            script: "git tag --list --sort=-version:refname 'v*' | head -n1",
            returnStdout: true
        ).trim()
        
        if (globalTag) {
            return globalTag.replaceAll('v', '')
        }
        
        // Final fallback based on commit count
        def commitCount = sh(
            script: "git rev-list --count HEAD",
            returnStdout: true
        ).trim() as Integer
        
        return commitCount > 10 ? "0.1.0" : "0.0.1"
    }
    
    /**
     * Setup Python environment with common dependencies
     */
    static def setupPythonEnvironment() {
        sh '''
            python -m venv venv
            . venv/bin/activate
            pip install --upgrade pip
            pip install -r requirements.txt
        '''
    }
    
    /**
     * Run code linting with common tools
     */
    static def runLinting() {
        sh '''
            . venv/bin/activate
            pip install flake8 black isort
            echo "Running code formatting checks..."
            black --check --diff . --exclude=venv/ || true
            echo "Running flake8 linting..."
            flake8 . --exclude=venv/ --max-line-length=88 --extend-ignore=E203,W503 || true
            echo "Running import sorting checks..."
            isort --check-only --diff . --skip=venv || true
        '''
    }
    
    /**
     * Run tests with coverage
     */
    static def runTests() {
        sh '''
            . venv/bin/activate
            pip install pytest pytest-cov pytest-asyncio
            echo "Running tests..."
            pytest --cov=. --cov-report=xml --cov-report=html --junitxml=test-results.xml || true
        '''
    }
    
    /**
     * Run security scans
     */
    static def runSecurityScans() {
        sh '''
            . venv/bin/activate
            pip install bandit safety pip-audit
            echo "Running security scans..."
            bandit -r . -f json -o bandit-report.json --exclude=./venv || true
            safety check --json --output=safety-report.json || true
            pip-audit --format=json --output=pip-audit-report.json || true
        '''
    }
    
    /**
     * Build Docker image with standard configuration
     */
    static def buildDockerImage(imageTag, ecrRegistry, ecrRepository) {
        def isPR = env.BRANCH_NAME?.startsWith('PR-')
        
        if (isPR) {
            echo "🔍 PR Build: Building image for validation (will NOT push to ECR)"
        } else {
            echo "🚀 Production Build: Building image for deployment"
        }
        
        sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo "Waiting for Docker daemon..."
            sleep 10
            docker version
            echo "Building Docker image: ${imageTag}"
            docker build -t ${ecrRegistry}/${ecrRepository}:${imageTag} .
        """
        
        // Create additional tags
        def commitSha = env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'unknown'
        def additionalTags = ["commit-${commitSha}", "build-${env.BUILD_NUMBER}"]
        
        if (isPR) {
            def prNumber = env.BRANCH_NAME.replaceAll('PR-', '')
            additionalTags.add("latest-pr-${prNumber}")
        } else {
            additionalTags.addAll(["latest", "stable"])
        }
        
        echo "Creating additional tags: ${additionalTags.join(', ')}"
        
        additionalTags.each { tag ->
            sh "docker tag ${ecrRegistry}/${ecrRepository}:${imageTag} ${ecrRegistry}/${ecrRepository}:${tag}"
        }
        
        echo "✅ Docker image built successfully with ${additionalTags.size() + 1} tags"
        
        return additionalTags
    }
    
    /**
     * Push Docker image to ECR
     */
    static def pushToEcr(imageTag, ecrRegistry, ecrRepository, additionalTags, awsRegion) {
        sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo "Authenticating to ECR..."
            aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${ecrRegistry}
            
            echo "Pushing primary image: ${ecrRegistry}/${ecrRepository}:${imageTag}"
            docker push ${ecrRegistry}/${ecrRepository}:${imageTag}
        """
        
        // Push additional tags
        additionalTags.each { tag ->
            sh "docker push ${ecrRegistry}/${ecrRepository}:${tag}"
        }
        
        echo "✅ Successfully pushed ${additionalTags.size() + 1} images to ECR"
        echo "🔗 Primary image: ${ecrRegistry}/${ecrRepository}:${imageTag}"
    }
    
    /**
     * Create and push Git tag
     */
    static def createGitTag(steps, imageTag) {
        def tagName = "backend/v${imageTag}"
        
        // Check if tag already exists
        def tagExists = steps.sh(
            script: "git tag -l '${tagName}'",
            returnStdout: true
        ).trim()
        
        if (tagExists) {
            steps.echo "ℹ️  Tag ${tagName} already exists, skipping creation"
            return
        }
        
        // Validate semantic version format
        if (imageTag ==~ /^\d+\.\d+\.\d+$/) {
            steps.sh """
                git config user.name "Jenkins CI"
                git config user.email "jenkins@luxe-jewelry.com"
                git tag -a '${tagName}' -m 'Backend release ${imageTag} - automated by Jenkins'
                git push origin '${tagName}'
            """
            steps.echo "✅ Created and pushed Git tag: ${tagName}"
        } else {
            steps.echo "ℹ️  Skipping Git tag creation for non-semantic version: ${imageTag}"
        }
    }
    
    /**
     * Common environment variables for backend builds
     */
    static def getEnvironmentVariables() {
        return [
            'AWS_DEFAULT_REGION': 'us-east-2',
            'ECR_REGISTRY': '950555670656.dkr.ecr.us-east-2.amazonaws.com',
            'ECR_REPOSITORY': 'jewleryapp/be',
            'SERVICE_NAME': 'backend'
        ]
    }
    
    /**
     * Pod template for backend builds
     */
    static def getPodTemplate() {
        return """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent
  containers:
  - name: python
    image: python:3.11-slim
    command: ['cat']
    tty: true
    resources:
      requests:
        memory: "256Mi"
        cpu: "100m"
      limits:
        memory: "512Mi"
        cpu: "500m"
  - name: docker-aws
    image: docker:24.0.6-dind
    securityContext:
      privileged: true
    env:
    - name: DOCKER_TLS_CERTDIR
      value: ""
    - name: DOCKER_HOST
      value: "tcp://localhost:2375"
    command:
    - sh
    - -c
    - |
      # Install AWS CLI in the Docker container
      apk add --no-cache aws-cli
      dockerd-entrypoint.sh
    resources:
      requests:
        memory: "512Mi"
        cpu: "200m"
      limits:
        memory: "1Gi"
        cpu: "1000m"
    tty: true
"""
    }
}
