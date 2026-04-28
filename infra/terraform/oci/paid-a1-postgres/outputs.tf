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

output "postgres_data_volume_id" {
  description = "PostgreSQL data Block Volume OCID."
  value       = oci_core_volume.postgres_data.id
}

output "postgres_data_volume_attachment_id" {
  description = "PostgreSQL data Block Volume attachment OCID."
  value       = oci_core_volume_attachment.postgres_data.id
}

output "postgres_private_connection_hint" {
  description = "SSH tunnel hint for local PostgreSQL access without public DB ingress."
  value       = "ssh -L 5432:127.0.0.1:5432 ubuntu@${oci_core_instance.this.public_ip}"
}

output "vcn_id" {
  description = "Created VCN OCID."
  value       = oci_core_vcn.this.id
}

output "subnet_id" {
  description = "Created public subnet OCID."
  value       = oci_core_subnet.public.id
}

output "selected_image_id" {
  description = "Image OCID used to launch the instance."
  value       = local.selected_image_id
}

output "selected_image_display_name" {
  description = "Display name of the automatically selected image, or source_image_ocid_override when pinned."
  value       = local.selected_image_display_name
}

output "selected_image_time_created" {
  description = "Creation time of the automatically selected image. Null when source_image_ocid_override is used."
  value       = local.selected_image_time_created
}
