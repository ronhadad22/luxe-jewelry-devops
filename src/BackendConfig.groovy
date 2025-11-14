/**
 * Backend Pipeline Configuration
 * Centralized configuration for backend CI/CD pipelines
 */

class BackendConfig {
    
    // AWS Configuration
    static final String AWS_REGION = 'us-east-2'
    static final String ECR_REGISTRY = '950555670656.dkr.ecr.us-east-2.amazonaws.com'
    static final String ECR_REPOSITORY = 'jewleryapp/be'
    
    // Service Configuration
    static final String SERVICE_NAME = 'backend'
    static final String PYTHON_VERSION = '3.11'
    static final String DOCKER_VERSION = '24.0.6'
    
    // Git Configuration
    static final String GIT_USER_NAME = 'Jenkins CI'
    static final String GIT_USER_EMAIL = 'jenkins@luxe-jewelry.com'
    
    // Pipeline Configuration
    static final Map<String, String> RESOURCE_LIMITS = [
        'python_memory_request': '256Mi',
        'python_memory_limit': '512Mi',
        'python_cpu_request': '100m',
        'python_cpu_limit': '500m',
        'docker_memory_request': '512Mi',
        'docker_memory_limit': '1Gi',
        'docker_cpu_request': '200m',
        'docker_cpu_limit': '1000m'
    ]
    
    // Tool Versions
    static final Map<String, String> TOOL_VERSIONS = [
        'flake8': 'latest',
        'black': 'latest',
        'isort': 'latest',
        'pytest': 'latest',
        'pytest-cov': 'latest',
        'pytest-asyncio': 'latest',
        'bandit': 'latest',
        'safety': 'latest',
        'pip-audit': 'latest'
    ]
    
    // Branch Patterns
    static final List<String> PRODUCTION_BRANCHES = ['main', 'master']
    static final List<String> DEVELOPMENT_BRANCHES = ['develop', 'development']
    static final List<String> BACKEND_BRANCHES = [
        'backend/PR-*',
        'PR-*',
        'backend/release/*',
        'feature/backend/*',
        'patch/backend/*'
    ]
    
    // Version Configuration
    static final String DEFAULT_VERSION = '0.0.1'
    static final String FALLBACK_VERSION = '0.1.0'
    static final Integer COMMIT_THRESHOLD = 10
    
    // Docker Configuration
    static final Integer DOCKER_WAIT_TIME = 10
    static final String DOCKER_HOST = 'tcp://localhost:2375'
    
    // Linting Configuration
    static final Map<String, Object> LINTING_CONFIG = [
        'max_line_length': 88,
        'extend_ignore': 'E203,W503',
        'exclude_dirs': ['venv', '__pycache__', '.git']
    ]
    
    // Test Configuration
    static final Map<String, String> TEST_CONFIG = [
        'coverage_format': 'xml,html',
        'junit_format': 'xml',
        'test_results_file': 'test-results.xml',
        'coverage_dir': 'htmlcov'
    ]
    
    // Security Scan Configuration
    static final Map<String, String> SECURITY_CONFIG = [
        'bandit_format': 'json',
        'bandit_output': 'bandit-report.json',
        'safety_format': 'json',
        'safety_output': 'safety-report.json',
        'pip_audit_format': 'json',
        'pip_audit_output': 'pip-audit-report.json'
    ]
    
    /**
     * Get environment variables as a map
     */
    static Map<String, String> getEnvironmentVariables() {
        return [
            'AWS_DEFAULT_REGION': AWS_REGION,
            'ECR_REGISTRY': ECR_REGISTRY,
            'ECR_REPOSITORY': ECR_REPOSITORY,
            'SERVICE_NAME': SERVICE_NAME,
            'DOCKER_HOST': DOCKER_HOST
        ]
    }
    
    /**
     * Get all backend branch patterns
     */
    static List<String> getAllBackendBranches() {
        return PRODUCTION_BRANCHES + DEVELOPMENT_BRANCHES + BACKEND_BRANCHES
    }
    
    /**
     * Check if branch is a production branch
     */
    static boolean isProductionBranch(String branchName) {
        return PRODUCTION_BRANCHES.contains(branchName)
    }
    
    /**
     * Check if branch is a PR branch
     */
    static boolean isPRBranch(String branchName) {
        return branchName?.startsWith('PR-') || branchName?.startsWith('backend/PR-')
    }
    
    /**
     * Check if branch is a backend-specific branch
     */
    static boolean isBackendBranch(String branchName) {
        return branchName?.startsWith('backend/') || 
               branchName?.startsWith('feature/backend/') || 
               branchName?.startsWith('patch/backend/')
    }
}
