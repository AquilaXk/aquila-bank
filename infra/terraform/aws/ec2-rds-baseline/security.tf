resource "aws_security_group" "ec2" {
  description = "SSH access for the baseline EC2 instance"
  name        = "${var.name_prefix}-ec2-sg"
  vpc_id      = aws_vpc.this.id

  ingress {
    cidr_blocks = [var.ssh_ingress_cidr]
    description = "SSH from operator CIDR"
    from_port   = 22
    protocol    = "tcp"
    to_port     = 22
  }

  egress {
    cidr_blocks = ["0.0.0.0/0"]
    description = "Outbound internet access"
    from_port   = 0
    protocol    = "-1"
    to_port     = 0
  }

  tags = {
    Name = "${var.name_prefix}-ec2-sg"
  }
}

resource "aws_security_group" "rds" {
  description = "PostgreSQL access from the baseline EC2 security group only"
  name        = "${var.name_prefix}-rds-sg"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "PostgreSQL from EC2"
    from_port       = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
    to_port         = 5432
  }

  egress {
    cidr_blocks = ["0.0.0.0/0"]
    description = "Outbound responses"
    from_port   = 0
    protocol    = "-1"
    to_port     = 0
  }

  tags = {
    Name = "${var.name_prefix}-rds-sg"
  }
}
