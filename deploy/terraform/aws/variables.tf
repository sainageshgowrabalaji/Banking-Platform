variable "region" {
  description = "AWS region. us-east-1 is the closest to the New York firms this project is modelled on."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Name used as a prefix for every resource."
  type        = string
  default     = "banking-platform"
}

variable "environment" {
  description = "dev, staging or prod."
  type        = string
  default     = "dev"
}
