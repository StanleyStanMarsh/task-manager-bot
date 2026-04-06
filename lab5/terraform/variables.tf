variable "folder_id" {
  type        = string
  description = "Yandex Cloud Folder ID"
}

variable "subnet_id" {
  type        = string
  description = "Subnet ID"
}

variable "security_group_id" {
  type        = string
  description = "Security group ID"
}

variable "ssh_public_key" {
  type        = string
  description = "Public SSH key"
}

variable "image_family" {
  type        = string
  default     = "ubuntu-2204-lts"
}

variable "token" {
  type        = string
  description = "Yandex Cloud IAM token"
}
