# Creates the Virtual Private Cloud (VPC)
resource "aws_vpc" "clickfila_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "clickfila-vpc"
  }
}

# Creates the Internet Gateway to allow external internet access
resource "aws_internet_gateway" "clickfila_igw" {
  vpc_id = aws_vpc.clickfila_vpc.id

  tags = {
    Name = "clickfila-igw"
  }
}

# Creates a Public Subnet in Availability Zone A
resource "aws_subnet" "clickfila_subnet_a" {
  vpc_id                  = aws_vpc.clickfila_vpc.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "us-east-1a"

  tags = {
    Name = "clickfila-subnet-a"
  }
}

# Creates a Public Subnet in Availability Zone B
resource "aws_subnet" "clickfila_subnet_b" {
  vpc_id                  = aws_vpc.clickfila_vpc.id
  cidr_block              = "10.0.2.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "us-east-1b"

  tags = {
    Name = "clickfila-subnet-b"
  }
}

# Creates a Route Table to direct internet traffic to the Gateway
resource "aws_route_table" "clickfila_route_table" {
  vpc_id = aws_vpc.clickfila_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.clickfila_igw.id
  }

  tags = {
    Name = "clickfila-route-table"
  }
}

# Associates Subnet A with the Route Table
resource "aws_route_table_association" "clickfila_rta_a" {
  subnet_id      = aws_subnet.clickfila_subnet_a.id
  route_table_id = aws_route_table.clickfila_route_table.id
}

# Associates Subnet B with the Route Table
resource "aws_route_table_association" "clickfila_rta_b" {
  subnet_id      = aws_subnet.clickfila_subnet_b.id
  route_table_id = aws_route_table.clickfila_route_table.id
}

# Creates a Security Group for the ECS/EC2 instances (Zero Trust - Outbound Only)
resource "aws_security_group" "clickfila_sg" {
  name        = "clickfila-sg"
  description = "Allow only outbound traffic for Cloudflare Tunnel and API calls"
  vpc_id      = aws_vpc.clickfila_vpc.id

  # Outbound rule (Allow all traffic to the internet so Cloudflared can connect)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "clickfila-sg"
  }
}