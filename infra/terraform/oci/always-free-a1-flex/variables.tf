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
  description = "OCI home region for Always Free block volume eligibility."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+-[0-9]+$", var.region))
    error_message = "region must look like ap-seoul-1 or us-ashburn-1."
  }
}

variable "compartment_ocid" {
  description = "Compartment OCID where the free-tier resources will be created."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^ocid1\\.compartment\\.", var.compartment_ocid)) || can(regex("^ocid1\\.tenancy\\.", var.compartment_ocid))
    error_message = "compartment_ocid must start with ocid1.compartment or ocid1.tenancy."
  }
}

variable "availability_domain" {
  description = "Availability domain name for the A1 Flex instance, for example Uocm:AP-SEOUL-1-AD-1."
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
  description = "Optional operating system version filter for automatic platform image lookup. Leave null to track the latest Ubuntu version for the shape."
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
  description = "CIDR allowed to connect to the staging Nginx HTTP port 80. Public staging uses 0.0.0.0/0."
  type        = string
  default     = "0.0.0.0/0"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.http_ingress_cidr))
    error_message = "http_ingress_cidr must be a valid IPv4 CIDR."
  }
}

variable "name_prefix" {
  description = "Name prefix for OCI resources."
  type        = string
  default     = "aquila-free-a1"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,30}[a-z0-9]$", var.name_prefix))
    error_message = "name_prefix must be lowercase kebab-case, 3-32 characters."
  }
}

variable "hostname_label" {
  description = "DNS hostname label for the primary VNIC."
  type        = string
  default     = "aquilafreea1"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,13}[a-z0-9]$", var.hostname_label))
    error_message = "hostname_label must be 3-15 lowercase alphanumeric characters."
  }
}

variable "vcn_dns_label" {
  description = "DNS label for the VCN."
  type        = string
  default     = "aquilavcn"
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
  default     = "10.40.0.0/16"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.vcn_cidr))
    error_message = "vcn_cidr must be a valid IPv4 CIDR."
  }
}

variable "subnet_cidr" {
  description = "IPv4 CIDR for the public subnet."
  type        = string
  default     = "10.40.1.0/24"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.subnet_cidr))
    error_message = "subnet_cidr must be a valid IPv4 CIDR."
  }
}

variable "instance_ocpus" {
  description = "OCPU count for VM.Standard.A1.Flex. Always Free upper bound is 4."
  type        = number
  default     = 4
  nullable    = false

  validation {
    condition     = var.instance_ocpus > 0 && var.instance_ocpus <= 4
    error_message = "instance_ocpus must be greater than 0 and no more than 4 for Always Free."
  }
}

variable "instance_memory_in_gbs" {
  description = "Memory in GB for VM.Standard.A1.Flex. Always Free upper bound is 24."
  type        = number
  default     = 24
  nullable    = false

  validation {
    condition     = var.instance_memory_in_gbs > 0 && var.instance_memory_in_gbs <= 24
    error_message = "instance_memory_in_gbs must be greater than 0 and no more than 24 for Always Free."
  }
}

variable "boot_volume_size_in_gbs" {
  description = "Boot volume size in GB. This stack caps it at the requested 150GB."
  type        = number
  default     = 150
  nullable    = false

  validation {
    condition     = var.boot_volume_size_in_gbs >= 50 && var.boot_volume_size_in_gbs <= 150
    error_message = "boot_volume_size_in_gbs must be between 50 and 150."
  }
}

variable "freeform_tags" {
  description = "Additional free-form tags for created OCI resources."
  type        = map(string)
  default     = {}
  nullable    = false
}
