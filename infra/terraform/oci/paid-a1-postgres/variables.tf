variable "tenancy_ocid" {
  description = "OCI tenancy OCID."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^ocid1\\.tenancy\\.", var.tenancy_ocid))
    error_message = "tenancy_ocid must start with ocid1.tenancy."
  }
}

variable "user_ocid" {
  description = "OCI user OCID for API key authentication."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^ocid1\\.user\\.", var.user_ocid))
    error_message = "user_ocid must start with ocid1.user."
  }
}

variable "fingerprint" {
  description = "OCI API key fingerprint."
  type        = string
  nullable    = false
  sensitive   = true

  validation {
    condition     = length(trimspace(var.fingerprint)) > 0
    error_message = "fingerprint must not be empty."
  }
}

variable "private_key_path" {
  description = "Local path to the OCI API private key. Do not commit the key."
  type        = string
  nullable    = false
  sensitive   = true

  validation {
    condition     = length(trimspace(var.private_key_path)) > 0
    error_message = "private_key_path must not be empty."
  }
}

variable "region" {
  description = "OCI region for the paid A1 PostgreSQL baseline."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+-[0-9]+$", var.region))
    error_message = "region must look like ap-seoul-1 or us-ashburn-1."
  }
}

variable "compartment_ocid" {
  description = "Compartment OCID where resources will be created."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^ocid1\\.compartment\\.", var.compartment_ocid)) || can(regex("^ocid1\\.tenancy\\.", var.compartment_ocid))
    error_message = "compartment_ocid must start with ocid1.compartment or ocid1.tenancy."
  }
}

variable "availability_domain" {
  description = "Availability domain name for the A1 Flex instance and data volume, for example Uocm:AP-SEOUL-1-AD-1."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.availability_domain)) > 0
    error_message = "availability_domain must not be empty."
  }
}

variable "source_image_ocid_override" {
  description = "Optional image OCID override. Use this when reproducible applies are more important than tracking the latest Ubuntu image."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.source_image_ocid_override == null || can(regex("^ocid1\\.image\\.", var.source_image_ocid_override))
    error_message = "source_image_ocid_override must be null or start with ocid1.image."
  }
}

variable "image_operating_system" {
  description = "Operating system filter used for automatic platform image lookup."
  type        = string
  default     = "Canonical Ubuntu"
  nullable    = false

  validation {
    condition     = length(trimspace(var.image_operating_system)) > 0
    error_message = "image_operating_system must not be empty."
  }
}

variable "image_operating_system_version" {
  description = "Optional operating system version filter for automatic platform image lookup. Leave null to track the latest Ubuntu image for the shape."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.image_operating_system_version == null || length(trimspace(var.image_operating_system_version)) > 0
    error_message = "image_operating_system_version must be null or a non-empty string."
  }
}

variable "ssh_public_key" {
  description = "SSH public key installed into the instance metadata."
  type        = string
  nullable    = false
  sensitive   = true

  validation {
    condition     = can(regex("^(ssh-rsa|ssh-ed25519|ecdsa-sha2-nistp256|ecdsa-sha2-nistp384|ecdsa-sha2-nistp521) ", trimspace(var.ssh_public_key)))
    error_message = "ssh_public_key must be a valid OpenSSH public key."
  }
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to connect to SSH port 22. Prefer a single operator IP /32."
  type        = string
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.ssh_ingress_cidr))
    error_message = "ssh_ingress_cidr must be a valid IPv4 CIDR."
  }
}

variable "http_ingress_cidr" {
  description = "Optional CIDR allowed to connect to HTTP port 80. Leave null to keep HTTP closed."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.http_ingress_cidr == null || can(cidrnetmask(var.http_ingress_cidr))
    error_message = "http_ingress_cidr must be null or a valid IPv4 CIDR."
  }
}

variable "https_ingress_cidr" {
  description = "Optional CIDR allowed to connect to HTTPS port 443. Leave null to keep HTTPS closed."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.https_ingress_cidr == null || can(cidrnetmask(var.https_ingress_cidr))
    error_message = "https_ingress_cidr must be null or a valid IPv4 CIDR."
  }
}

variable "name_prefix" {
  description = "Name prefix for OCI resources."
  type        = string
  default     = "aquila-paid-a1-pg"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,30}[a-z0-9]$", var.name_prefix))
    error_message = "name_prefix must be lowercase kebab-case, 3-32 characters."
  }
}

variable "hostname_label" {
  description = "DNS hostname label for the primary VNIC."
  type        = string
  default     = "aquilapaidpg"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,13}[a-z0-9]$", var.hostname_label))
    error_message = "hostname_label must be 3-15 lowercase alphanumeric characters."
  }
}

variable "vcn_dns_label" {
  description = "DNS label for the VCN."
  type        = string
  default     = "aquilapgvcn"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,13}[a-z0-9]$", var.vcn_dns_label))
    error_message = "vcn_dns_label must be 3-15 lowercase alphanumeric characters."
  }
}

variable "subnet_dns_label" {
  description = "DNS label for the public subnet."
  type        = string
  default     = "public"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,13}[a-z0-9]$", var.subnet_dns_label))
    error_message = "subnet_dns_label must be 3-15 lowercase alphanumeric characters."
  }
}

variable "vcn_cidr" {
  description = "IPv4 CIDR for the dedicated VCN."
  type        = string
  default     = "10.60.0.0/16"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.vcn_cidr))
    error_message = "vcn_cidr must be a valid IPv4 CIDR."
  }
}

variable "subnet_cidr" {
  description = "IPv4 CIDR for the public subnet."
  type        = string
  default     = "10.60.1.0/24"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.subnet_cidr))
    error_message = "subnet_cidr must be a valid IPv4 CIDR."
  }
}

variable "instance_ocpus" {
  description = "OCPU count for VM.Standard.A1.Flex."
  type        = number
  default     = 4
  nullable    = false

  validation {
    condition     = var.instance_ocpus > 0 && var.instance_ocpus <= 16
    error_message = "instance_ocpus must be between 1 and 16 for this low-cost A1 baseline."
  }
}

variable "instance_memory_in_gbs" {
  description = "Memory in GB for VM.Standard.A1.Flex."
  type        = number
  default     = 24
  nullable    = false

  validation {
    condition     = var.instance_memory_in_gbs >= var.instance_ocpus && var.instance_memory_in_gbs <= 64
    error_message = "instance_memory_in_gbs must be between 1GB/OCPU and 64GB for this low-cost baseline."
  }
}

variable "boot_volume_size_in_gbs" {
  description = "Boot volume size in GiB."
  type        = number
  default     = 50
  nullable    = false

  validation {
    condition     = var.boot_volume_size_in_gbs >= 50 && var.boot_volume_size_in_gbs <= 200
    error_message = "boot_volume_size_in_gbs must be between 50 and 200."
  }
}

variable "postgres_data_volume_size_in_gbs" {
  description = "PostgreSQL data Block Volume size in GiB."
  type        = number
  default     = 200
  nullable    = false

  validation {
    condition     = var.postgres_data_volume_size_in_gbs >= 200 && var.postgres_data_volume_size_in_gbs <= 1024
    error_message = "postgres_data_volume_size_in_gbs must be between 200 and 1024."
  }
}

variable "postgres_data_volume_vpus_per_gb" {
  description = "OCI Block Volume performance units per GB. 10 is Balanced."
  type        = number
  default     = 10
  nullable    = false

  validation {
    condition     = contains([0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120], var.postgres_data_volume_vpus_per_gb)
    error_message = "postgres_data_volume_vpus_per_gb must be 0, 10, 20, or a supported Ultra High Performance step through 120."
  }
}

variable "postgres_data_device" {
  description = "Expected paravirtualized Linux device path for the attached PostgreSQL data volume."
  type        = string
  default     = "/dev/oracleoci/oraclevdb"
  nullable    = false

  validation {
    condition     = startswith(var.postgres_data_device, "/dev/")
    error_message = "postgres_data_device must be an absolute /dev path."
  }
}

variable "postgres_data_mount_path" {
  description = "Mount path for PostgreSQL data volume on the instance."
  type        = string
  default     = "/var/lib/aquila-postgres"
  nullable    = false

  validation {
    condition     = startswith(var.postgres_data_mount_path, "/") && !endswith(var.postgres_data_mount_path, "/")
    error_message = "postgres_data_mount_path must be an absolute path without trailing slash."
  }
}

variable "postgres_image" {
  description = "PostgreSQL container image."
  type        = string
  default     = "postgres:18"
  nullable    = false

  validation {
    condition     = can(regex("^postgres:18", var.postgres_image))
    error_message = "postgres_image must use PostgreSQL 18."
  }
}

variable "postgres_database" {
  description = "Default PostgreSQL database name."
  type        = string
  default     = "aquila_bank"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9_]{1,62}$", var.postgres_database))
    error_message = "postgres_database must be a valid lowercase PostgreSQL identifier."
  }
}

variable "postgres_user" {
  description = "Default PostgreSQL application user."
  type        = string
  default     = "aquila"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9_]{1,62}$", var.postgres_user))
    error_message = "postgres_user must be a valid lowercase PostgreSQL identifier."
  }
}

variable "freeform_tags" {
  description = "Additional free-form tags for created OCI resources."
  type        = map(string)
  default     = {}
  nullable    = false
}
