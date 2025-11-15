/**
 * Shared Frontend Pipeline Library
 * Contains common functions and configurations for frontend CI/CD
 */

class FrontendPipeline {
    
    /**
     * Determine if current build is executing for a pull request
     */
    static boolean isPullRequest(def env) {
        def branchName = env?.BRANCH_NAME
        return env?.CHANGE_ID || branchName?.startsWith('PR-') || branchName?.startsWith('frontend/PR-')
    }

    /**
     * Determine if current build is executing on the main release branch
     */
    static boolean isMainBranch(def env) {
        def branchName = env?.BRANCH_NAME
        return branchName in ['main', 'master']
    }

    /**
     * Determine whether linting/tests should run for the current branch
     */
    static boolean shouldRunQualityChecks(def env) {
        if (isPullRequest(env) || isMainBranch(env)) {
            return true
        }

        def branchName = env?.BRANCH_NAME ?: ''
        if (!branchName) {
            return true
        }

        def patterns = [
            ~/^feature\/.*/,
            ~/^frontend\/.*/,
            ~/^patch\/.*/,
            ~/^hotfix\/.*/,
            ~/^release\/.*/,
            ~/^develop(ment)?$/
        ]

        return patterns.any { branchName ==~ it }
    }

    /**
     * Determine the next semantic release version
     */
    static def determineReleaseVersion(steps, env) {
        def override = env?.RELEASE_VERSION_OVERRIDE
        if (override) {
            steps.echo "Using override release version: ${override}"
            return override.trim()
        }

        def bumpType = (env?.RELEASE_BUMP_TYPE ?: 'patch').toLowerCase()
        def supportedBumps = ['major', 'minor', 'patch']
        if (!supportedBumps.contains(bumpType)) {
            steps.echo "Unsupported RELEASE_BUMP_TYPE '${bumpType}', defaulting to 'patch'"
            bumpType = 'patch'
        }

        def latestTag = getLatestFrontendTag(steps)
        if (!latestTag) {
            steps.echo 'No previous frontend tags found; starting at 0.1.0'
            return '0.1.0'
        }

        def parts = latestTag.tokenize('.')
        if (parts.size() != 3 || !parts.every { it.isInteger() }) {
            steps.echo "Latest tag '${latestTag}' is not a valid semantic version; using fallback 0.1.0"
            return '0.1.0'
        }

        def major = parts[0] as Integer
        def minor = parts[1] as Integer
        def patch = parts[2] as Integer

        switch (bumpType) {
            case 'major':
                major++
                minor = 0
                patch = 0
                break
            case 'minor':
                minor++
                patch = 0
                break
            default:
                patch++
                break
        }

        def releaseVersion = "${major}.${minor}.${patch}"
        steps.echo "Calculated next release version ${releaseVersion} (previous ${latestTag}, bump ${bumpType})"
        return releaseVersion
    }

    /**
     * Get latest frontend version tag
     */
    static def getLatestFrontendTag(steps) {
        def latestTag = steps.sh(
            script: "git tag --list --sort=-version:refname 'frontend/v*' | head -n1",
            returnStdout: true
        ).trim()
        
        if (latestTag) {
            return latestTag.replaceAll('frontend/v', '')
        }
        
        // Fallback to global tags if no service-specific tags
        def globalTag = steps.sh(
            script: "git tag --list --sort=-version:refname 'v*' | head -n1",
            returnStdout: true
        ).trim()
        
        if (globalTag) {
            return globalTag.replaceAll('v', '')
        }
        
        // Final fallback based on commit count
        def commitCount = steps.sh(
            script: "git rev-list --count HEAD",
            returnStdout: true
        ).trim() as Integer
        
        return commitCount > 10 ? "0.1.0" : "0.0.1"
    }
    
    /**
     * Setup Node environment with dependencies
     */
    static def setupNodeEnvironment(steps) {
        steps.sh '''
            node --version
            npm --version
            echo "Installing dependencies..."
            npm ci
        '''
    }
    
    /**
     * Run code linting
     */
    static def runLinting(steps) {
        steps.sh '''
            echo "Running ESLint..."
            npm run lint || true
            echo "Checking code formatting with Prettier..."
            npx prettier --check src/ || true
        '''
    }
    
    /**
     * Run tests with coverage
     */
    static def runTests(steps) {
        steps.sh '''
            echo "Running tests..."
            npm test -- --coverage --watchAll=false || true
        '''
    }
    
    /**
     * Run security audit
     */
    static def runSecurityAudit(steps) {
        steps.sh '''
            echo "Running npm audit..."
            npm audit --audit-level=moderate --json > npm-audit-report.json || true
        '''
    }
    
    /**
     * Run bundle analysis
     */
    static def runBundleAnalysis(steps) {
        steps.sh '''
            echo "Checking for unused dependencies..."
            npx depcheck || true
        '''
    }
    
    /**
     * Build React application
     */
    static def buildApplication(steps) {
        steps.sh '''
            echo "Building React application..."
            npm run build
            echo "Build completed successfully"
            ls -la build/
        '''
    }
    
    /**
     * Build a one-off validation image for PR builds (no push)
     */
    static def buildValidationImage(steps, env, ecrRegistry, ecrRepository) {
        def buildNumber = env?.BUILD_NUMBER ?: 'local'
        def validationTag = "pr-validation-${buildNumber}"

        steps.echo "Building temporary validation image '${validationTag}' to ensure Dockerfile integrity"
        steps.sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo \"Waiting for Docker daemon...\"
            sleep 10
            docker version
            docker build -t ${ecrRegistry}/${ecrRepository}:${validationTag} .
            docker images --format \"table {{.Repository}}\t{{.Tag}}\t{{.ID}}\" | head -n 5
            docker rmi ${ecrRegistry}/${ecrRepository}:${validationTag} || true
        """
    }

    /**
     * Build the release image once and tag with environment-specific versions
     */
    static def buildAndTagReleaseImage(steps, env, releaseVersion, ecrRegistry, ecrRepository) {
        def tempTag = "build-temp-${env?.BUILD_NUMBER ?: 'local'}"
        def releaseTags = [
            "${releaseVersion}-dev",
            "${releaseVersion}-rc",
            releaseVersion
        ]

        steps.echo "Building Docker image once with temporary tag '${tempTag}'"
        steps.sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo \"Waiting for Docker daemon...\"
            sleep 10
            docker version
            docker build -t ${ecrRegistry}/${ecrRepository}:${tempTag} .
        """

        steps.echo "Tagging release image for environments: ${releaseTags.join(', ')}"
        releaseTags.each { tag ->
            steps.sh "docker tag ${ecrRegistry}/${ecrRepository}:${tempTag} ${ecrRegistry}/${ecrRepository}:${tag}"
        }

        return [tempTag: tempTag, releaseTags: releaseTags]
    }

    /**
     * Push tagged release images to ECR
     */
    static void pushTaggedImages(steps, ecrRegistry, ecrRepository, releaseTags, awsRegion) {
        if (!releaseTags) {
            steps.echo 'No release tags provided, skipping push'
            return
        }

        steps.sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo \"Authenticating to ECR...\"
            aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${ecrRegistry}
        """

        releaseTags.each { tag ->
            steps.sh "docker push ${ecrRegistry}/${ecrRepository}:${tag}"
        }

        steps.echo "✅ Pushed ${releaseTags.size()} release images to ECR"
    }

    /**
     * Create and push Git tag
     */
    static def createGitTag(steps, imageTag) {
        def tagName = "frontend/v${imageTag}"
        
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
                git tag -a '${tagName}' -m 'Frontend release ${imageTag} - automated by Jenkins'
                git push origin '${tagName}'
            """
            steps.echo "✅ Created and pushed Git tag: ${tagName}"
        } else {
            steps.echo "ℹ️  Skipping Git tag creation for non-semantic version: ${imageTag}"
        }
    }
    
    /**
     * Common environment variables for frontend builds
     */
    static def getEnvironmentVariables() {
        return [
            'AWS_DEFAULT_REGION': 'us-east-2',
            'ECR_REGISTRY': '950555670656.dkr.ecr.us-east-2.amazonaws.com',
            'ECR_REPOSITORY': 'jewleryapp/fe',
            'SERVICE_NAME': 'jewelry-store'
        ]
    }
    
    /**
     * Pod template for frontend builds
     */
    static def getPodTemplate() {
        return """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent
  containers:
  - name: node
    image: node:18-alpine
    command: ['cat']
    tty: true
    resources:
      requests:
        memory: "512Mi"
        cpu: "200m"
      limits:
        memory: "1Gi"
        cpu: "1000m"
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
        memory: "1Gi"
        cpu: "500m"
      limits:
        memory: "2Gi"
        cpu: "2000m"
    tty: true
"""
    }
}
