resource "aws_db_instance" "postgres" {
  allocated_storage            = var.db_allocated_storage
  auto_minor_version_upgrade   = true
  backup_retention_period      = var.db_backup_retention_period
  copy_tags_to_snapshot        = true
  db_name                      = var.db_name
  db_subnet_group_name         = aws_db_subnet_group.this.name
  deletion_protection          = false
  engine                       = "postgres"
  engine_version               = var.db_engine_version
  identifier                   = var.db_identifier
  instance_class               = var.db_instance_class
  max_allocated_storage        = var.db_max_allocated_storage
  monitoring_interval          = 0
  multi_az                     = false
  password                     = var.db_password
  performance_insights_enabled = false
  port                         = 5432
  publicly_accessible          = false
  skip_final_snapshot          = true
  storage_encrypted            = true
  storage_type                 = "gp3"
  username                     = var.db_username
  vpc_security_group_ids       = [aws_security_group.rds.id]

  tags = {
    Name = var.db_identifier
  }
}
