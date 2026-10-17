terraform {
  backend "s3" {
    bucket = "clickfila-terraform-state"
    key    = "infra/terraform.tfstate"
    region = "us-east-1"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "ClickFila"
      Environment = "Production"
      ManagedBy   = "Terraform"
    }
  }
}