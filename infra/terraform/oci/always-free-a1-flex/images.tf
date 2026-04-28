# 최신 Ubuntu 추적용 조회. 재현성 우선 운영은 source_image_ocid_override로 image OCID를 고정한다.
data "oci_core_images" "ubuntu_a1" {
  count = var.source_image_ocid_override == null ? 1 : 0

  compartment_id           = var.tenancy_ocid
  operating_system         = var.image_operating_system
  operating_system_version = var.image_operating_system_version
  shape                    = local.instance_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
  state                    = "AVAILABLE"
}
