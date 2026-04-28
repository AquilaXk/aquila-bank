locals {
  instance_shape = "VM.Standard.A1.Flex"

  selected_image_id = var.source_image_ocid_override == null ? data.oci_core_images.ubuntu_a1[0].images[0].id : var.source_image_ocid_override

  selected_image_display_name = var.source_image_ocid_override == null ? data.oci_core_images.ubuntu_a1[0].images[0].display_name : "source_image_ocid_override"
  selected_image_time_created = var.source_image_ocid_override == null ? data.oci_core_images.ubuntu_a1[0].images[0].time_created : null

  common_tags = merge(
    {
      CostBoundary = "oci-always-free"
      ManagedBy    = "terraform"
      Project      = "aquila-bank"
    },
    var.freeform_tags
  )
}
