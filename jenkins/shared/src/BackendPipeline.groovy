/**
 * Shared Backend Pipeline Library
 * Contains common functions and configurations for backend CI/CD
 */

class BackendPipeline {
    
    /**
     * Determine if current build is executing for a pull request
     */
    static boolean isPullRequest(def env) {
        def branchName = env?.BRANCH_NAME
        return env?.CHANGE_ID || branchName?.startsWith('PR-') || branchName?.startsWith('backend/PR-')
    }

    /**
     * Determine if current build is executing on the main release branch
     */
    static boolean isMainBranch(def env) {
        def branchName = env?.BRANCH_NAME
        return branchName in ['main', 'master']
    }

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

        def latestTag = getLatestBackendTag(steps)
        if (!latestTag) {
            steps.echo 'No previous backend tags found; starting at 0.1.0'
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
     * Get latest backend version tag
     */
    static def getLatestBackendTag(steps) {
        def latestTag = steps.sh(
            script: "git tag --list --sort=-version:refname 'backend/v*' | head -n1",
            returnStdout: true
        ).trim()
        
        if (latestTag) {
            return latestTag.replaceAll('backend/v', '')
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
     * Setup Python environment with common dependencies
     */
    static def setupPythonEnvironment(steps) {
        steps.sh '''
            python -m venv venv
            . venv/bin/activate
            pip install --upgrade pip
            pip install -r requirements.txt
        '''
    }
    
    /**
     * Run code linting with common tools
     */
    static def runLinting(steps) {
        steps.sh '''
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
    static def runTests(steps) {
        steps.sh '''
            . venv/bin/activate
            pip install pytest pytest-cov pytest-asyncio
            echo "Running tests..."
            pytest --cov=. --cov-report=xml --cov-report=html --junitxml=test-results.xml || true
        '''
    }
    
    /**
     * Run security scans
     */
    static def runSecurityScans(steps) {
        steps.sh '''
            . venv/bin/activate
            pip install bandit safety pip-audit
            echo "Running security scans..."
            bandit -r . -f json -o bandit-report.json --exclude=./venv || true
            safety check --json --output=safety-report.json || true
            pip-audit --format=json --output=pip-audit-report.json || true
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
     * Build Docker image with standard configuration
     */
    static def buildDockerImage(steps, env, imageTag, ecrRegistry, ecrRepository) {
        def isPR = env?.BRANCH_NAME?.startsWith('PR-')
        
        if (isPR) {
            steps.echo "🔍 PR Build: Building image for validation (will NOT push to ECR)"
        } else {
            steps.echo "🚀 Production Build: Building image for deployment"
        }
        
        steps.sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo "Waiting for Docker daemon..."
            sleep 10
            docker version
            echo "Building Docker image: ${imageTag}"
            docker build -t ${ecrRegistry}/${ecrRepository}:${imageTag} .
        """
        
        // Create additional tags
        def commitSha = env?.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'unknown'
        def buildNumber = env?.BUILD_NUMBER ?: 'unknown'
        def additionalTags = ["commit-${commitSha}", "build-${buildNumber}"]
        
        if (isPR) {
            def prNumber = env?.BRANCH_NAME?.replaceAll('PR-', '') ?: 'unknown'
            additionalTags.add("latest-pr-${prNumber}")
        } else {
            additionalTags.addAll(["latest", "stable"])
        }
        
        steps.echo "Creating additional tags: ${additionalTags.join(', ')}"
        
        additionalTags.each { tag ->
            steps.sh "docker tag ${ecrRegistry}/${ecrRepository}:${imageTag} ${ecrRegistry}/${ecrRepository}:${tag}"
        }
        
        steps.echo "✅ Docker image built successfully with ${additionalTags.size() + 1} tags"
        
        return additionalTags
    }
    
    /**
     * Push Docker image to ECR
     */
    static def pushToEcr(steps, imageTag, ecrRegistry, ecrRepository, additionalTags, awsRegion) {
        steps.sh """
            export DOCKER_HOST=tcp://localhost:2375
            echo "Authenticating to ECR..."
            aws ecr get-login-password --region ${awsRegion} | docker login --username AWS --password-stdin ${ecrRegistry}
            
            echo "Pushing primary image: ${ecrRegistry}/${ecrRepository}:${imageTag}"
            docker push ${ecrRegistry}/${ecrRepository}:${imageTag}
        """
        
        // Push additional tags
        additionalTags.each { tag ->
            steps.sh "docker push ${ecrRegistry}/${ecrRepository}:${tag}"
        }
        
        steps.echo "✅ Successfully pushed ${additionalTags.size() + 1} images to ECR"
        steps.echo "🔗 Primary image: ${ecrRegistry}/${ecrRepository}:${imageTag}"
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
     * Update the GitOps dev environment manifest with the new image tag
     */
    static void updateGitOpsManifest(steps, env, releaseVersion) {
        def repoUrl = env?.GITOPS_REPO_URL
        if (!repoUrl) {
            steps.echo 'No GitOps repository configured; skipping manifest update'
            return
        }

        def credentialsId = env?.GITOPS_REPO_CREDENTIALS_ID
        def repoWithoutProtocol = repoUrl.replaceFirst('^https?://', '')
        def imageReference = "${env?.ECR_REGISTRY}/${env?.ECR_REPOSITORY}:${releaseVersion}-dev"

        steps.sh 'apk add --no-cache git > /dev/null 2>&1 || true'

        steps.dir('gitops-workspace') {
            steps.deleteDir()

            def cloneCommand = credentialsId ?
                "git clone https://$GITOPS_USERNAME:$GITOPS_PASSWORD@${repoWithoutProtocol} repo" :
                "git clone ${repoUrl} repo"

            if (credentialsId) {
                steps.withCredentials([
                    steps.usernamePassword(
                        credentialsId: credentialsId,
                        passwordVariable: 'GITOPS_PASSWORD',
                        usernameVariable: 'GITOPS_USERNAME'
                    )
                ]) {
                    steps.sh """
                        set -e
                        ${cloneCommand}
                    """
                }
            } else {
                steps.sh """
                    set -e
                    ${cloneCommand}
                """
            }

            steps.dir('repo') {
                steps.sh """
                    set -e
                    mkdir -p dev
                    if [ -f dev/values.yaml ]; then
                        sed -i.bak -E 's|image:\\s*.*|image: ${imageReference}|' dev/values.yaml
                    else
                        echo "image: ${imageReference}" > dev/values.yaml
                    fi
                    rm -f dev/values.yaml.bak
                """

                steps.sh """
                    set -e
                    git config user.name '${env?.GITOPS_COMMIT_USER ?: 'Jenkins CI'}'
                    git config user.email '${env?.GITOPS_COMMIT_EMAIL ?: 'jenkins@luxe-jewelry.com'}'
                """

                steps.sh """
                    set -e
                    if git status --short | grep -q .; then
                        git add dev/values.yaml
                        git commit -m 'Update dev image to ${imageReference}'
                        git push origin HEAD
                    else
                        echo 'No manifest changes detected; skipping commit'
                    fi
                """
            }
        }
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
