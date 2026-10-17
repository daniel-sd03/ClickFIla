# Fetches the latest Amazon Linux 2023 Image (AMI)
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

# Creates the ECS Cluster
resource "aws_ecs_cluster" "clickfila_cluster" {
  name = "clickfila-cluster"
}

# Creates a Placeholder Task Definition just to bootstrap the Service
resource "aws_ecs_task_definition" "dummy_task" {
  family                   = "clickfila-dummy"
  network_mode             = "bridge"
  requires_compatibilities = ["EC2"]
  cpu                      = "256"
  memory                   = "256"

  container_definitions = jsonencode([{
    name      = "dummy"
    image     = "alpine"
    cpu       = 256
    memory    = 256
    essential = true
    command   = ["sleep", "3600"]
  }])
}

# Creates the ECS Service
resource "aws_ecs_service" "clickfila_service" {
  name            = "clickfila-service"
  cluster         = aws_ecs_cluster.clickfila_cluster.id
  task_definition = aws_ecs_task_definition.dummy_task.arn
  desired_count   = 1

  # Ignore future changes to the task definition made by GitHub Actions
  lifecycle {
    ignore_changes = [task_definition]
  }
}

# Creates the IAM Role for the EC2 machine
resource "aws_iam_role" "ecs_instance_role" {
  name = "clickfila-ecs-instance-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

# Attaches the required permission for the EC2 to talk to ECS
resource "aws_iam_role_policy_attachment" "ecs_instance_role_attachment" {
  role       = aws_iam_role.ecs_instance_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

# Connects the Role to the EC2 machine
resource "aws_iam_instance_profile" "ecs_instance_profile" {
  name = "clickfila-ecs-instance-profile"
  role = aws_iam_role.ecs_instance_role.name
}

# Creates the EC2 Instance
resource "aws_instance" "clickfila_ec2" {
  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = "t3.small"
  subnet_id              = aws_subnet.clickfila_subnet_a.id
  vpc_security_group_ids = [aws_security_group.clickfila_sg.id]
  iam_instance_profile   = aws_iam_instance_profile.ecs_instance_profile.name

  user_data = file("../aws/ec2-user-data.sh")

  tags = {
    Name = "clickfila-ec2"
  }
}