locals {
  instance_shape = "VM.Standard.A1.Flex"

  selected_image_id = var.source_image_ocid_override == null ? data.oci_core_images.ubuntu_a1[0].images[0].id : var.source_image_ocid_override

  selected_image_display_name = var.source_image_ocid_override == null ? data.oci_core_images.ubuntu_a1[0].images[0].display_name : "source_image_ocid_override"
  selected_image_time_created = var.source_image_ocid_override == null ? data.oci_core_images.ubuntu_a1[0].images[0].time_created : null

  http_ingress_cidrs  = var.http_ingress_cidr == null ? [] : [var.http_ingress_cidr]
  https_ingress_cidrs = var.https_ingress_cidr == null ? [] : [var.https_ingress_cidr]

  common_tags = merge(
    {
      CostBoundary = "oci-paid-a1-postgres"
      ManagedBy    = "terraform"
      Project      = "aquila-bank"
    },
    var.freeform_tags
  )
}
