locals {
  instance_shape = "VM.Standard.A1.Flex"

  common_tags = merge(
    {
      CostBoundary = "oci-always-free"
      ManagedBy    = "terraform"
      Project      = "aquila-bank"
    },
    var.freeform_tags
  )
}
