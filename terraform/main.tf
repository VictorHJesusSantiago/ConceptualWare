/**
 * Concept #22 — Infrastructure as Code (IaC): Terraform
 *
 * Terraform declaratively defines infrastructure (VPC, EKS, RDS, S3, IAM)
 * and manages its lifecycle (create, update, destroy).
 *
 * Key concepts:
 *   Provider:    plugin to manage a cloud (AWS, GCP, Azure, Kubernetes)
 *   Resource:    infrastructure object (aws_instance, aws_vpc, aws_eks_cluster)
 *   Data Source: read existing resources without managing them
 *   Variable:    input parameter to the configuration
 *   Output:      exported values after apply
 *   State:       terraform.tfstate tracks real-world resource state
 *   Module:      reusable configuration unit (like a function)
 *
 * Workflow:
 *   terraform init     — install providers, initialize backend
 *   terraform plan     — preview changes (no-op on real infra)
 *   terraform apply    — create/update resources
 *   terraform destroy  — delete all managed resources
 *   terraform import   — bring existing resources under Terraform management
 *
 * State backends: S3 + DynamoDB (locking), Terraform Cloud, GCS
 */

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.30"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
  }

  # Remote state: S3 backend with DynamoDB locking
  # Prevents concurrent terraform apply (state corruption)
  backend "s3" {
    bucket         = "conceptualware-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    kms_key_id     = "alias/terraform-state"
    dynamodb_table = "terraform-state-lock"  # locking table
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "ConceptualWare"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Team        = "Platform"
      CostCenter  = "Engineering"
    }
  }
}

# ── VPC ──────────────────────────────────────────────────────────────────────

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.5"

  name = "conceptualware-${var.environment}"
  cidr = var.vpc_cidr

  azs             = data.aws_availability_zones.available.names
  private_subnets = var.private_subnets
  public_subnets  = var.public_subnets

  enable_nat_gateway   = true    # NAT for private subnet → internet
  single_nat_gateway   = var.environment == "prod" ? false : true
  enable_dns_hostnames = true
  enable_dns_support   = true

  # EKS requires specific subnet tags
  public_subnet_tags = {
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
    "kubernetes.io/role/elb"                       = "1"     # public subnets for ALB
  }
  private_subnet_tags = {
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
    "kubernetes.io/role/internal-elb"              = "1"     # private subnets for internal ALB
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  cluster_name = "conceptualware-eks-${var.environment}"
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.cluster_name
  cluster_version = "1.29"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Control plane access
  cluster_endpoint_public_access  = false   # private endpoint only
  cluster_endpoint_private_access = true

  # KMS encryption for secrets at rest
  cluster_encryption_config = {
    provider_key_arn = aws_kms_key.eks.arn
    resources        = ["secrets"]
  }

  # Managed node groups
  eks_managed_node_groups = {
    # General workloads
    general = {
      instance_types = ["m6i.xlarge"]
      min_size       = 2
      max_size       = 10
      desired_size   = 3

      disk_size = 50
      disk_type = "gp3"

      labels = {
        role = "general"
      }
    }

    # Spot instances for batch/ML workloads (cost savings ~70%)
    spot = {
      capacity_type  = "SPOT"
      instance_types = ["m6i.2xlarge", "m6a.2xlarge", "m5.2xlarge"]
      min_size       = 0
      max_size       = 20
      desired_size   = 0

      labels = {
        role                            = "spot"
        "node.kubernetes.io/lifecycle"  = "spot"
      }

      taints = [{
        key    = "spot"
        value  = "true"
        effect = "NO_SCHEDULE"  # only pods that tolerate spot run here
      }]
    }
  }

  # AWS add-ons
  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent              = true
      service_account_role_arn = module.vpc_cni_irsa.iam_role_arn
    }
    aws-ebs-csi-driver = {
      most_recent              = true
      service_account_role_arn = module.ebs_csi_irsa.iam_role_arn
    }
  }
}

# ── RDS (PostgreSQL for analytics) ───────────────────────────────────────────

resource "aws_db_subnet_group" "main" {
  name       = "conceptualware-${var.environment}"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_db_instance" "analytics" {
  identifier = "conceptualware-analytics-${var.environment}"

  engine            = "postgres"
  engine_version    = "15.4"
  instance_class    = var.rds_instance_class
  allocated_storage = 100
  storage_type      = "gp3"
  storage_encrypted = true
  kms_key_id        = aws_kms_key.rds.arn

  db_name  = "conceptualware"
  username = "admin"
  password = random_password.rds.result  # auto-generated, stored in Secrets Manager

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az               = var.environment == "prod"  # HA for prod
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  performance_insights_enabled = true
  monitoring_interval          = 60   # Enhanced Monitoring

  deletion_protection = var.environment == "prod"

  tags = {
    Name = "conceptualware-analytics-${var.environment}"
  }
}

resource "random_password" "rds" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "rds_password" {
  name                    = "conceptualware/${var.environment}/rds-password"
  kms_key_id              = aws_kms_key.secrets.arn
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "rds_password" {
  secret_id     = aws_secretsmanager_secret.rds_password.id
  secret_string = jsonencode({
    username = aws_db_instance.analytics.username
    password = random_password.rds.result
    host     = aws_db_instance.analytics.address
    port     = aws_db_instance.analytics.port
    database = aws_db_instance.analytics.db_name
  })
}

# ── S3 (Object Storage) ───────────────────────────────────────────────────────

resource "aws_s3_bucket" "ml_models" {
  bucket = "conceptualware-ml-models-${var.environment}-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_versioning" "ml_models" {
  bucket = aws_s3_bucket.ml_models.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "ml_models" {
  bucket = aws_s3_bucket.ml_models.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
  }
}

resource "aws_s3_bucket_public_access_block" "ml_models" {
  bucket                  = aws_s3_bucket.ml_models.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Lifecycle policy: transition to cheaper storage tiers after time
resource "aws_s3_bucket_lifecycle_configuration" "ml_models" {
  bucket = aws_s3_bucket.ml_models.id
  rule {
    id     = "model-archiving"
    status = "Enabled"
    transition {
      days          = 30
      storage_class = "STANDARD_IA"   # Infrequent Access after 30 days
    }
    transition {
      days          = 90
      storage_class = "GLACIER"       # Archive after 90 days
    }
    expiration {
      days = 365  # delete after 1 year
    }
  }
}

# ── KMS Keys ──────────────────────────────────────────────────────────────────

resource "aws_kms_key" "eks"     { description = "EKS secrets encryption" }
resource "aws_kms_key" "rds"     { description = "RDS storage encryption" }
resource "aws_kms_key" "s3"      { description = "S3 bucket encryption" }
resource "aws_kms_key" "secrets" { description = "Secrets Manager encryption" }

# ── IAM ───────────────────────────────────────────────────────────────────────

# IRSA (IAM Role for Service Accounts): gives Kubernetes pods AWS permissions
module "vpc_cni_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.33"

  role_name = "vpc-cni-${var.environment}"

  attach_vpc_cni_policy = true
  vpc_cni_enable_ipv4   = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-node"]
    }
  }
}

module "ebs_csi_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.33"

  role_name = "ebs-csi-${var.environment}"
  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }
}

data "aws_caller_identity" "current" {}

# ── Security Groups ────────────────────────────────────────────────────────────

resource "aws_security_group" "rds" {
  name   = "conceptualware-rds-${var.environment}"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]  # only EKS nodes
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
