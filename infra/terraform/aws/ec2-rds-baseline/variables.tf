variable "aws_region" {
  description = "AWS region for EC2 and RDS resources."
  type        = string
  default     = "ap-northeast-2"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z]{2}-[a-z]+-[0-9]+$", var.aws_region))
    error_message = "aws_region must look like ap-northeast-2."
  }
}

variable "name_prefix" {
  description = "Name prefix for AWS resources."
  type        = string
  default     = "aquila-bank-baseline"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,40}[a-z0-9]$", var.name_prefix))
    error_message = "name_prefix must be lowercase kebab-case, 3-42 characters."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the dedicated VPC."
  type        = string
  default     = "10.50.0.0/16"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR."
  }
}

variable "public_subnet_cidr" {
  description = "CIDR block for the public EC2 subnet."
  type        = string
  default     = "10.50.1.0/24"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.public_subnet_cidr))
    error_message = "public_subnet_cidr must be a valid IPv4 CIDR."
  }
}

variable "private_db_subnet_cidrs" {
  description = "Two private subnet CIDR blocks for the RDS subnet group."
  type        = list(string)
  default     = ["10.50.11.0/24", "10.50.12.0/24"]
  nullable    = false

  validation {
    condition     = length(var.private_db_subnet_cidrs) == 2 && alltrue([for cidr in var.private_db_subnet_cidrs : can(cidrnetmask(cidr))])
    error_message = "private_db_subnet_cidrs must contain exactly two valid IPv4 CIDRs."
  }
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to connect to EC2 SSH port 22. Prefer a single operator IP /32."
  type        = string
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.ssh_ingress_cidr))
    error_message = "ssh_ingress_cidr must be a valid IPv4 CIDR."
  }
}

variable "ec2_key_name" {
  description = "Existing AWS EC2 key pair name for SSH."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.ec2_key_name)) > 0
    error_message = "ec2_key_name must not be empty."
  }
}

variable "ec2_ami_id" {
  description = "Optional EC2 AMI ID override. Leave null to use the latest Amazon Linux 2023 x86_64 AMI."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.ec2_ami_id == null || can(regex("^ami-[0-9a-f]+$", var.ec2_ami_id))
    error_message = "ec2_ami_id must be null or start with ami-."
  }
}

variable "ec2_instance_type" {
  description = "EC2 instance type. Baseline target is t3.micro."
  type        = string
  default     = "t3.micro"
  nullable    = false

  validation {
    condition     = var.ec2_instance_type == "t3.micro"
    error_message = "This baseline intentionally allows only t3.micro."
  }
}

variable "ec2_root_volume_size" {
  description = "EC2 root EBS volume size in GiB."
  type        = number
  default     = 30
  nullable    = false

  validation {
    condition     = var.ec2_root_volume_size >= 8 && var.ec2_root_volume_size <= 100
    error_message = "ec2_root_volume_size must be between 8 and 100 GiB."
  }
}

variable "db_identifier" {
  description = "RDS DB instance identifier."
  type        = string
  default     = "aquila-bank-baseline-postgres"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,61}[a-z0-9]$", var.db_identifier))
    error_message = "db_identifier must be lowercase kebab-case, 3-63 characters."
  }
}

variable "db_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "aquilabank"
  nullable    = false

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,62}$", var.db_name))
    error_message = "db_name must be a PostgreSQL-compatible identifier."
  }
}

variable "db_username" {
  description = "RDS master username."
  type        = string
  default     = "aquila_admin"
  nullable    = false

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9_]{0,62}$", var.db_username))
    error_message = "db_username must be a PostgreSQL-compatible identifier."
  }
}

variable "db_password" {
  description = "RDS master password. Pass through terraform.tfvars or TF_VAR_db_password; do not commit it."
  type        = string
  nullable    = false
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 16
    error_message = "db_password must be at least 16 characters."
  }
}

variable "db_engine_version" {
  description = "Optional PostgreSQL engine major or minor version. Null lets AWS select the regional default supported version."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.db_engine_version == null || can(regex("^[0-9]+(\\.[0-9]+)?$", var.db_engine_version))
    error_message = "db_engine_version must be null or a major/minor PostgreSQL version such as 17 or 17.5."
  }
}

variable "db_instance_class" {
  description = "RDS instance class. Baseline target is db.t4g.small."
  type        = string
  default     = "db.t4g.small"
  nullable    = false

  validation {
    condition     = var.db_instance_class == "db.t4g.small"
    error_message = "This baseline intentionally allows only db.t4g.small."
  }
}

variable "db_allocated_storage" {
  description = "Initial RDS storage in GiB."
  type        = number
  default     = 100
  nullable    = false

  validation {
    condition     = var.db_allocated_storage >= 20 && var.db_allocated_storage <= 1024
    error_message = "db_allocated_storage must be between 20 and 1024 GiB."
  }
}

variable "db_max_allocated_storage" {
  description = "Optional RDS storage autoscaling ceiling in GiB. Null disables autoscaling."
  type        = number
  default     = null
  nullable    = true

  validation {
    condition     = var.db_max_allocated_storage == null || var.db_max_allocated_storage >= var.db_allocated_storage
    error_message = "db_max_allocated_storage must be null or greater than/equal to db_allocated_storage."
  }
}

variable "db_backup_retention_period" {
  description = "RDS backup retention period in days."
  type        = number
  default     = 1
  nullable    = false

  validation {
    condition     = var.db_backup_retention_period >= 0 && var.db_backup_retention_period <= 7
    error_message = "db_backup_retention_period must be between 0 and 7 for this cost-controlled baseline."
  }
}

variable "freeform_tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
  nullable    = false
}
