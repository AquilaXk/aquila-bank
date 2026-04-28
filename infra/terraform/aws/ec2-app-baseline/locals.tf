locals {
  common_tags = merge(
    {
      CostBoundary = "aws-ec2-app-baseline"
      ManagedBy    = "terraform"
      Project      = "aquila-bank"
    },
    var.freeform_tags
  )

  ec2_ami_id = var.ec2_ami_id == null ? data.aws_ami.amazon_linux_2023.id : var.ec2_ami_id
}
