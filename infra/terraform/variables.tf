variable "cloud_id" {
  type        = string
  description = "Yandex Cloud cloud id"
}

variable "folder_id" {
  type        = string
  description = "Yandex Cloud folder id"
}

variable "zone" {
  type        = string
  description = "Compute zone"
  default     = "ru-central1-a"
}

variable "vm_name" {
  type        = string
  description = "Compute instance name"
  default     = "task-manager-bot"
}

variable "vm_platform_id" {
  type        = string
  description = "Compute platform id"
  default     = "standard-v3"
}

variable "vm_cores" {
  type        = number
  description = "vCPU cores"
  default     = 2
}

variable "vm_memory_gb" {
  type        = number
  description = "RAM in GB"
  default     = 4
}

variable "vm_core_fraction" {
  type        = number
  description = "Core fraction"
  default     = 100
}

variable "vm_disk_gb" {
  type        = number
  description = "Boot disk size in GB"
  default     = 30
}

variable "ssh_username" {
  type        = string
  description = "Linux username used for SSH"
  default     = "ubuntu"
}

variable "ssh_public_key" {
  type        = string
  description = "SSH public key text (single line)"
}

variable "network_cidr" {
  type        = string
  description = "VPC network CIDR"
  default     = "10.10.0.0/16"
}

variable "subnet_cidr" {
  type        = string
  description = "Subnet CIDR"
  default     = "10.10.0.0/24"
}

