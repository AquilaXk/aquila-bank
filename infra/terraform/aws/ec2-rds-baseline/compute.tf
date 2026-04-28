resource "aws_instance" "app" {
  ami                         = local.ec2_ami_id
  associate_public_ip_address = true
  instance_type               = var.ec2_instance_type
  key_name                    = var.ec2_key_name
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.ec2.id]

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    delete_on_termination = true
    encrypted             = true
    volume_size           = var.ec2_root_volume_size
    volume_type           = "gp3"
  }

  tags = {
    Name = "${var.name_prefix}-ec2"
  }
}
