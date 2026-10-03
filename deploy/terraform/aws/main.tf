# Phase 4 fills this in. Nothing is created today, so running this costs nothing.
#
# Planned, in the order they are added
#   1. Network     a VPC with public and private subnets across three zones
#   2. Cluster     EKS, with the services in private subnets
#   3. Database    RDS for PostgreSQL, Multi-AZ, encrypted, one database per service
#   4. Messaging   MSK for Kafka
#   5. Edge        an Application Load Balancer in front of the API gateway, with TLS
#   6. Secrets     AWS Secrets Manager, read by the pods through IAM roles
#   7. State       this Terraform state in S3 with locking

locals {
  name = "${var.project}-${var.environment}"
}
