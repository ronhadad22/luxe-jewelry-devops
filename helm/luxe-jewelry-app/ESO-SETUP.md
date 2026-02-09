# External Secrets Operator (ESO) Setup

This document explains how to use External Secrets Operator to manage secrets from AWS Secrets Manager.

## Overview

The application uses ESO to fetch secrets from AWS Secrets Manager instead of storing them directly in Kubernetes.

## Prerequisites

1. **ESO installed in cluster** ✅ (Already deployed)
2. **AWS Secrets Manager secret created** ✅
3. **IAM role with proper permissions** (Required)
4. **ServiceAccount with IAM role annotation** (Configured in values.yaml)

## AWS Secrets Manager Secret

**Secret ARN**: `arn:aws:secretsmanager:us-east-1:950555670656:secret:jewlery-app/auth-givYmd`

**Secret Structure**:
```json
{
  "jwt_secret": "your-actual-jwt-secret-value"
}
```

## IAM Role Setup

### Required IAM Policy

Create an IAM policy with the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:950555670656:secret:jewlery-app/auth-*"
    }
  ]
}
```

### IAM Role Trust Policy

Create an IAM role named `luxe-jewelry-external-secrets-role` with this trust policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::950555670656:oidc-provider/oidc.eks.us-east-2.amazonaws.com/id/YOUR_CLUSTER_OIDC_ID"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "oidc.eks.us-east-2.amazonaws.com/id/YOUR_CLUSTER_OIDC_ID:sub": "system:serviceaccount:luxe-jewelry:luxe-jewelry-app",
          "oidc.eks.us-east-2.amazonaws.com/id/YOUR_CLUSTER_OIDC_ID:aud": "sts.amazonaws.com"
        }
      }
    }
  ]
}
```

**Note**: Replace `YOUR_CLUSTER_OIDC_ID` with your actual EKS cluster OIDC provider ID.

### Get OIDC Provider ID

```bash
aws eks describe-cluster --name your-cluster-name --query "cluster.identity.oidc.issuer" --output text | cut -d '/' -f 5
```

## Kubernetes Resources Created

### 1. SecretStore
- **Name**: `luxe-jewelry-app-aws-secretstore`
- **Purpose**: Defines connection to AWS Secrets Manager
- **Authentication**: Uses ServiceAccount with IAM role (IRSA)

### 2. ExternalSecret
- **Name**: `luxe-jewelry-app-auth-external`
- **Purpose**: Fetches JWT secret from AWS Secrets Manager
- **Target Secret**: `auth-secrets`
- **Refresh Interval**: 1 hour (configurable)

### 3. Kubernetes Secret (Auto-generated)
- **Name**: `auth-secrets`
- **Created by**: External Secrets Operator
- **Contains**: `jwt-secret` key (mapped from `jwt_secret` in AWS)

## Configuration

### Enable/Disable ESO

In `values.yaml`:

```yaml
externalSecrets:
  enabled: true  # Set to false to use static Kubernetes secrets
  refreshInterval: 1h
  aws:
    region: us-east-1
  auth:
    secretArn: "arn:aws:secretsmanager:us-east-1:950555670656:secret:jewlery-app/auth-givYmd"
```

### ServiceAccount IAM Role

In `values.yaml`:

```yaml
security:
  serviceAccount:
    create: true
    annotations:
      eks.amazonaws.com/role-arn: "arn:aws:iam::950555670656:role/luxe-jewelry-external-secrets-role"
```

## Deployment

### Deploy with ESO enabled

```bash
helm upgrade --install luxe-jewelry-app ./helm/luxe-jewelry-app \
  --namespace luxe-jewelry \
  --values ./helm/luxe-jewelry-app/values.yaml
```

### Verify ESO Resources

```bash
# Check SecretStore
kubectl get secretstore -n luxe-jewelry
# OR
kubectl get clustersecretstore -n luxe-jewelry

# Check ExternalSecret
kubectl get externalsecret -n luxe-jewelry

# Check if secret was created
kubectl get secret auth-secrets -n luxe-jewelry

# Check ExternalSecret status
kubectl describe externalsecret luxe-jewelry-app-auth-external -n luxe-jewelry
```

## Troubleshooting

### ExternalSecret not syncing

1. **Check ExternalSecret status**:
   ```bash
   kubectl describe externalsecret luxe-jewelry-app-auth-external -n luxe-jewelry
   ```

2. **Check SecretStore status**:
   ```bash
   kubectl describe secretstore luxe-jewelry-app-aws-secretstore -n luxe-jewelry
   ```

3. **Verify IAM role annotation**:
   ```bash
   kubectl get sa luxe-jewelry-app -n luxe-jewelry -o yaml
   ```

4. **Check ESO logs**:
   ```bash
   kubectl logs -n external-secrets-system deployment/external-secrets
   ```

### Common Issues

1. **Access Denied**: IAM role doesn't have permission to access the secret
2. **Secret not found**: Wrong ARN or secret doesn't exist
3. **Invalid key**: The property `jwt_secret` doesn't exist in AWS secret
4. **IRSA not working**: ServiceAccount annotation missing or incorrect

## Fallback to Static Secrets

If ESO is not working, you can disable it:

```yaml
externalSecrets:
  enabled: false
```

This will create a static Kubernetes secret using the value from:

```yaml
auth:
  secrets:
    jwtSecret: "your-secret-key-change-in-production-please-use-strong-random-value"
```

## Security Best Practices

1. ✅ Use ESO in production (secrets stored in AWS Secrets Manager)
2. ✅ Use IAM roles (IRSA) instead of access keys
3. ✅ Rotate secrets regularly (ESO will auto-sync)
4. ✅ Use least-privilege IAM policies
5. ✅ Monitor ExternalSecret sync status
6. ⚠️ Never commit actual secret values to Git

## Secret Rotation

When you rotate the secret in AWS Secrets Manager:

1. Update the secret value in AWS Secrets Manager
2. ESO will automatically sync the new value within the refresh interval (1 hour)
3. Pods will need to be restarted to pick up the new secret:
   ```bash
   kubectl rollout restart deployment luxe-jewelry-app-auth -n luxe-jewelry
   ```

Or force immediate sync:
```bash
kubectl annotate externalsecret luxe-jewelry-app-auth-external -n luxe-jewelry \
  force-sync=$(date +%s) --overwrite
```
