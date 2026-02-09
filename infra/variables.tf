variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "eu-west-1"
}

variable "aws_profile" {
  description = "AWS CLI profile to use"
  type        = string
  default     = "iitc-profile"
}

variable "environment" {
  description = "Environment name (e.g. dev, staging, production)"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "luxe-jewelry"
}

# VPC
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.20.0/24"]
}

# ECS
variable "backend_container_port" {
  description = "Port the backend container listens on"
  type        = number
  default     = 8000
}

variable "auth_container_port" {
  description = "Port the auth-service container listens on"
  type        = number
  default     = 8000
}

variable "backend_cpu" {
  description = "CPU units for backend task (1 vCPU = 1024)"
  type        = number
  default     = 256
}

variable "backend_memory" {
  description = "Memory (MiB) for backend task"
  type        = number
  default     = 512
}

variable "auth_cpu" {
  description = "CPU units for auth-service task (1 vCPU = 1024)"
  type        = number
  default     = 256
}

variable "auth_memory" {
  description = "Memory (MiB) for auth-service task"
  type        = number
  default     = 512
}

variable "backend_desired_count" {
  description = "Desired number of backend tasks"
  type        = number
  default     = 2
}

variable "auth_desired_count" {
  description = "Desired number of auth-service tasks"
  type        = number
  default     = 2
}

# Secrets
variable "jwt_secret_key" {
  description = "JWT secret key for token signing"
  type        = string
  sensitive   = true
  default     = "your-secret-key-change-in-production"
}
