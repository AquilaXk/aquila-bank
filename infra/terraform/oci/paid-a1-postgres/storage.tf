resource "oci_core_volume" "postgres_data" {
  availability_domain = var.availability_domain
  compartment_id      = var.compartment_ocid
  display_name        = "${var.name_prefix}-postgres-data"
  freeform_tags       = local.common_tags
  size_in_gbs         = var.postgres_data_volume_size_in_gbs
  vpus_per_gb         = var.postgres_data_volume_vpus_per_gb
}

resource "oci_core_volume_attachment" "postgres_data" {
  attachment_type = "paravirtualized"
  device          = var.postgres_data_device
  display_name    = "${var.name_prefix}-postgres-data-attachment"
  instance_id     = oci_core_instance.this.id
  is_read_only    = false
  is_shareable    = false
  volume_id       = oci_core_volume.postgres_data.id
}
