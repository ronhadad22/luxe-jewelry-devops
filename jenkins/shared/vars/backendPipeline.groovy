def getLatestBackendTag() {
    def latestTag = ''
    try {
        latestTag = sh(
            script: 'git tag --list --sort=-version:refname "backend/v*" | head -n1',
            returnStdout: true
        ).trim()
        
        if (latestTag) {
            return latestTag.replaceAll('backend/v', '')
        }
    } catch (Exception e) {
        // Fallback logic
    }
    
    // If no service-specific tags, use smart defaults
    try {
        def commitCount = sh(
            script: 'git rev-list --count HEAD',
            returnStdout: true
        ).trim() as Integer
        
        return commitCount > 100 ? '0.1.0' : '0.0.1'
    } catch (Exception e) {
        return '0.0.1'
    }
}

def generateImageTag(branchName, latestTag, buildNumber, commitSha) {
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
        case ~/^backend\/release\/.*/:
            def releaseVersion = branchName.replaceAll('backend/release/', '')
            imageTag = "${releaseVersion}-rc.${buildNumber}"
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
        default:
            def cleanBranch = branchName.replaceAll('[^a-zA-Z0-9]', '-').toLowerCase()
            imageTag = "${latestTag}-${cleanBranch}.${buildNumber}.${commitSha}"
            break
    }
    return imageTag
}

def setupPythonEnvironment() {
    sh '''
        python -m venv venv
        . venv/bin/activate
        pip install --upgrade pip
        pip install -r requirements.txt
    '''
}

def runLinting() {
    sh '''
        . venv/bin/activate
        pip install flake8 black isort
        echo "Running code formatting checks..."
        black --check --diff . --exclude="venv/"
        echo "Running flake8 linting..."
        flake8 . --max-line-length=88 --extend-ignore=E203,W503 --exclude=venv/
        echo "Running import sorting checks..."
        isort --check-only --diff . --skip-glob="venv/*"
        echo "✅ All linting checks passed!"
    '''
}

def runTests() {
    sh '''
        . venv/bin/activate
        pip install pytest pytest-cov pytest-asyncio
        echo "Running tests..."
        pytest --cov=. --cov-report=xml --cov-report=html --junitxml=test-results.xml || true
    '''
}

def runSecurityScans() {
    sh '''
        . venv/bin/activate
        pip install bandit safety pip-audit
        echo "Running security scans..."
        bandit -r . -f json -o bandit-report.json --exclude="./venv" || true
        safety check --output json > safety-report.json || true
        pip-audit --format=json --output=pip-audit-report.json || true
    '''
}

def buildDockerImage(imageTag, ecrRegistry, ecrRepository) {
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

def pushToEcr(imageTag, ecrRegistry, ecrRepository, additionalTags, awsRegion) {
    sh """
        export DOCKER_HOST=tcp://localhost:2375
        echo "Authenticating to ECR..."
        aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${ecrRegistry}
        
        echo "Pushing primary image: ${ecrRegistry}/${ecrRepository}:${imageTag}"
        docker push ${ecrRegistry}/${ecrRepository}:${imageTag}
    """
    
    additionalTags.each { tag ->
        sh "docker push ${ecrRegistry}/${ecrRepository}:${tag}"
    }
    
    echo "✅ Successfully pushed ${additionalTags.size() + 1} images to ECR"
    echo "🔗 Primary image: ${ecrRegistry}/${ecrRepository}:${imageTag}"
}

def createGitTag(imageTag) {
    def tagName = "backend/v${imageTag}"
    
    // Check if tag already exists
    def tagExists = sh(script: "git tag -l '${tagName}'", returnStdout: true).trim()
    
    if (tagExists) {
        echo "ℹ️  Tag ${tagName} already exists, skipping creation"
        return
    }
    
    // Validate semantic version format
    if (imageTag ==~ /^\d+\.\d+\.\d+$/) {
        sh """
            git config user.name "Jenkins CI"
            git config user.email "jenkins@luxe-jewelry.com"
            git tag -a '${tagName}' -m 'Backend release ${imageTag} - automated by Jenkins'
            git push origin '${tagName}'
        """
        echo "✅ Created and pushed Git tag: ${tagName}"
    } else {
        echo "ℹ️  Skipping Git tag creation for non-semantic version: ${imageTag}"
    }
}

def getPodTemplate() {
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
