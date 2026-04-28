resource "oci_core_instance" "this" {
  availability_domain  = var.availability_domain
  compartment_id       = var.compartment_ocid
  display_name         = var.name_prefix
  freeform_tags        = local.common_tags
  preserve_boot_volume = false
  shape                = local.instance_shape

  create_vnic_details {
    assign_public_ip = true
    display_name     = "${var.name_prefix}-primary-vnic"
    hostname_label   = var.hostname_label
    subnet_id        = oci_core_subnet.public.id
  }

  instance_options {
    are_legacy_imds_endpoints_disabled = true
  }

  metadata = {
    ssh_authorized_keys = trimspace(var.ssh_public_key)
  }

  shape_config {
    memory_in_gbs = var.instance_memory_in_gbs
    ocpus         = var.instance_ocpus
  }

  source_details {
    boot_volume_size_in_gbs = var.boot_volume_size_in_gbs
    source_id               = local.selected_image_id
    source_type             = "image"
  }
}
