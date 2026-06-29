# Concept #22 — Terraform Bootstrap
#
# Before using the main Terraform configuration (which stores state in S3),
# the S3 bucket and DynamoDB table must first be created.
# This bootstrap configuration creates those resources using LOCAL state.
#
# IMPORTANT: Run this ONCE before running main Terraform:
#   cd terraform/bootstrap && terraform init && terraform apply
#
# After applying, copy the outputs into terraform/main.tf backend block.
# Then run: cd ../.. && terraform init -reconfigure
#
# Terraform state for the bootstrap itself is stored locally (terraform.tfstate).
# Commit it to a PRIVATE repo or store securely — it tracks the backend resources.

terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  # Bootstrap uses LOCAL state (no S3 backend yet — chicken-and-egg problem)
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  description = "AWS region for state backend resources"
  default     = "us-east-1"
}

variable "project" {
  description = "Project name prefix"
  default     = "conceptualware"
}

variable "environment" {
  description = "Deployment environment"
  default     = "shared"
}

# ── S3 bucket for Terraform state ────────────────────────────────────────────

resource "aws_s3_bucket" "terraform_state" {
  bucket = "${var.project}-terraform-state-${data.aws_caller_identity.current.account_id}"

  lifecycle {
    # Prevent accidental deletion — state loss = infrastructure drift
    prevent_destroy = true
  }

  tags = {
    Name        = "${var.project}-terraform-state"
    Purpose     = "Terraform state backend"
    ManagedBy   = "terraform-bootstrap"
    Environment = var.environment
  }
}

# Versioning: every state write creates a new version (point-in-time recovery)
resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Server-side encryption at rest (KMS-managed)
resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}

# Block all public access — state files contain secrets
resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ── DynamoDB table for state locking ─────────────────────────────────────────
#
# Prevents concurrent Terraform applies from corrupting state.
# LockID is the lock key — only one Terraform process holds it at a time.

resource "aws_dynamodb_table" "terraform_locks" {
  name         = "${var.project}-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"   # no capacity planning needed (rarely used)
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name        = "${var.project}-terraform-locks"
    Purpose     = "Terraform state locking"
    ManagedBy   = "terraform-bootstrap"
    Environment = var.environment
  }
}

# ── IAM policy for Terraform operator role ────────────────────────────────────
#
# Least-privilege: only the permissions needed for Terraform to manage state.

resource "aws_iam_policy" "terraform_state_access" {
  name        = "${var.project}-terraform-state-access"
  description = "Allow Terraform operators to manage state in S3 and DynamoDB"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "S3StateAccess"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket",
          "s3:GetBucketVersioning"
        ]
        Resource = [
          aws_s3_bucket.terraform_state.arn,
          "${aws_s3_bucket.terraform_state.arn}/*"
        ]
      },
      {
        Sid    = "DynamoDBLocking"
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:DeleteItem"
        ]
        Resource = aws_dynamodb_table.terraform_locks.arn
      }
    ]
  })
}

data "aws_caller_identity" "current" {}

# ── Outputs — copy into main Terraform backend block ─────────────────────────

output "state_bucket_name" {
  description = "S3 bucket name for Terraform state"
  value       = aws_s3_bucket.terraform_state.bucket
}

output "dynamodb_table_name" {
  description = "DynamoDB table name for state locking"
  value       = aws_dynamodb_table.terraform_locks.name
}

output "backend_config" {
  description = "Paste this into terraform/main.tf backend block"
  value = <<-EOT
    backend "s3" {
      bucket         = "${aws_s3_bucket.terraform_state.bucket}"
      key            = "conceptualware/terraform.tfstate"
      region         = "${var.aws_region}"
      encrypt        = true
      dynamodb_table = "${aws_dynamodb_table.terraform_locks.name}"
    }
  EOT
}
