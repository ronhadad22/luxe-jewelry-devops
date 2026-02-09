# Luxe Jewelry Store - ECS Infrastructure

Terraform configuration to deploy the Luxe Jewelry Store backend and auth-service on AWS ECS Fargate.

## Architecture

```
Internet
   │
   ▼
[ ALB ] ──── Port 80
   │
   ├── /auth/*  ──▶  Auth Service (Fargate)
   │
   └── /*       ──▶  Backend Service (Fargate)
```

### Resources Created

- **VPC** with public/private subnets across 2 AZs
- **NAT Gateway** for private subnet internet access
- **Application Load Balancer** with path-based routing
- **ECR Repositories** for Docker images (backend + auth-service)
- **ECS Cluster** with Fargate launch type
- **ECS Services** with health checks and CloudWatch logging
- **Security Groups** with least-privilege access

## Prerequisites

- [Terraform](https://www.terraform.io/downloads) >= 1.5
- [AWS CLI](https://aws.amazon.com/cli/) configured with credentials
- [Docker](https://www.docker.com/) for building images
- S3 bucket for Terraform state (update `main.tf` backend config)

## Quick Start

### 1. Configure Terraform Backend

Update the S3 backend in `main.tf` with your bucket name, or comment it out to use local state:

```hcl
# In main.tf, update or comment out:
backend "s3" {
  bucket = "your-terraform-state-bucket"
  ...
}
```

### 2. Set Variables

```bash
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values
```

### 3. Deploy Infrastructure

```bash
cd infra
terraform init
terraform plan
terraform apply
```

### 4. Build & Push Docker Images

```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Backend
docker build -t luxe-backend ../backend
docker tag luxe-backend:latest $(terraform output -raw backend_ecr_url):latest
docker push $(terraform output -raw backend_ecr_url):latest

# Auth Service
docker build -t luxe-auth ../auth-service
docker tag luxe-auth:latest $(terraform output -raw auth_ecr_url):latest
docker push $(terraform output -raw auth_ecr_url):latest
```

### 5. Force New Deployment (after pushing new images)

```bash
aws ecs update-service --cluster $(terraform output -raw ecs_cluster_name) --service $(terraform output -raw backend_service_name) --force-new-deployment
aws ecs update-service --cluster $(terraform output -raw ecs_cluster_name) --service $(terraform output -raw auth_service_name) --force-new-deployment
```

## Accessing the API

After deployment, get the ALB URL:

```bash
terraform output alb_url
```

- **Backend API**: `http://<ALB_DNS>/`
- **Auth Service**: `http://<ALB_DNS>/auth/`
- **Swagger (Backend)**: `http://<ALB_DNS>/docs`

## Tear Down

```bash
terraform destroy
```
