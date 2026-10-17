# Creates the ECR repository for the Click Fila API
resource "aws_ecr_repository" "clickfila_api" {
  name                 = "clickfila-api"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Lifecycle policy to keep only the 5 most recent images and delete the older ones
resource "aws_ecr_lifecycle_policy" "clickfila_cleanup" {
  repository = aws_ecr_repository.clickfila_api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the 5 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = {
        type = "expire"
      }
    }]
  })
}