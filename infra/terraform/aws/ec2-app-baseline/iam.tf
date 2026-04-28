data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      identifiers = ["ec2.amazonaws.com"]
      type        = "Service"
    }
  }
}

resource "aws_iam_role" "app" {
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
  name               = "${var.name_prefix}-app-ec2-role"

  tags = {
    Name = "${var.name_prefix}-app-ec2-role"
  }
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  role       = aws_iam_role.app.name
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.name_prefix}-app-ec2-profile"
  role = aws_iam_role.app.name

  tags = {
    Name = "${var.name_prefix}-app-ec2-profile"
  }
}
