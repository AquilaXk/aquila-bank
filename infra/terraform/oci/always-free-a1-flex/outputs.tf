output "instance_id" {
  description = "Created OCI compute instance OCID."
  value       = oci_core_instance.this.id
}

output "instance_public_ip" {
  description = "Public IPv4 address assigned to the instance VNIC."
  value       = oci_core_instance.this.public_ip
}

output "instance_private_ip" {
  description = "Private IPv4 address assigned to the instance VNIC."
  value       = oci_core_instance.this.private_ip
}

output "vcn_id" {
  description = "Created VCN OCID."
  value       = oci_core_vcn.this.id
}

output "subnet_id" {
  description = "Created public subnet OCID."
  value       = oci_core_subnet.public.id
}
