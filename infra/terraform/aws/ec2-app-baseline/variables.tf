variable "aws_region" {
  description = "AWS region for the EC2 app baseline resources."
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
  description = "EC2 instance type. App baseline target is t3.small."
  type        = string
  default     = "t3.small"
  nullable    = false

  validation {
    condition     = var.ec2_instance_type == "t3.small"
    error_message = "This app baseline intentionally allows only t3.small."
  }
}

variable "ec2_root_volume_size" {
  description = "EC2 root EBS volume size in GiB."
  type        = number
  default     = 40
  nullable    = false

  validation {
    condition     = var.ec2_root_volume_size == 40
    error_message = "ec2_root_volume_size must be 40 GiB for this app baseline."
  }
}

variable "freeform_tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
  nullable    = false
}
