output "ec2_instance_id" {
  description = "EC2 instance ID."
  value       = aws_instance.app.id
}

output "ec2_public_dns" {
  description = "EC2 public DNS name."
  value       = aws_instance.app.public_dns
}

output "ec2_public_ip" {
  description = "EC2 public IPv4 address."
  value       = aws_instance.app.public_ip
}

output "selected_ec2_ami_id" {
  description = "AMI ID used by the EC2 instance."
  value       = local.ec2_ami_id
}

output "vpc_id" {
  description = "Created VPC ID."
  value       = aws_vpc.this.id
}
