output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "alb_dns_name" {
  description = "ALB DNS name - use this to access the API"
  value       = aws_lb.main.dns_name
}

output "alb_url" {
  description = "Full ALB URL"
  value       = "http://${aws_lb.main.dns_name}"
}

output "backend_ecr_url" {
  description = "ECR repository URL for backend"
  value       = aws_ecr_repository.backend.repository_url
}

output "auth_ecr_url" {
  description = "ECR repository URL for auth-service"
  value       = aws_ecr_repository.auth.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "backend_service_name" {
  description = "Backend ECS service name"
  value       = aws_ecs_service.backend.name
}

output "auth_service_name" {
  description = "Auth-service ECS service name"
  value       = aws_ecs_service.auth.name
}

output "swagger_backend_url" {
  description = "Swagger UI URL for backend API"
  value       = "http://${aws_lb.main.dns_name}/docs"
}

output "swagger_auth_url" {
  description = "Swagger UI URL for auth-service API"
  value       = "http://${aws_lb.main.dns_name}/auth/docs"
}

output "frontend_bucket_name" {
  description = "S3 bucket name for frontend static files"
  value       = aws_s3_bucket.frontend.id
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID"
  value       = aws_cloudfront_distribution.frontend.id
}

output "cloudfront_url" {
  description = "CloudFront URL for the frontend"
  value       = "https://${aws_cloudfront_distribution.frontend.domain_name}"
}

output "github_actions_role_arn" {
  description = "IAM Role ARN for GitHub Actions - set this as AWS_ROLE_ARN secret in your repo"
  value       = aws_iam_role.github_actions.arn
}
