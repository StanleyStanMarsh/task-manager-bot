terraform {
  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = "~> 0.130"
    }
  }
  required_version = ">= 1.0"
}

provider "yandex" {
  token     = var.token
  folder_id = var.folder_id
}

# ---------- DATA ----------

data "yandex_vpc_subnet" "main" {
  subnet_id = var.subnet_id
}

data "yandex_compute_image" "ubuntu" {
  family = var.image_family
}

# ---------- DISK (Mongo) ----------

resource "yandex_compute_disk" "mongo_vol" {
  name = "mongo-vol"
  size = 10
  type = "network-hdd"
  zone = data.yandex_vpc_subnet.main.zone
}

# ---------- VM ----------

resource "yandex_compute_instance" "bot_server" {
  name        = "task-management-bot-pluzh"
  platform_id = "standard-v3"
  zone        = data.yandex_vpc_subnet.main.zone

  resources {
    cores  = 2
    memory = 2
  }

  boot_disk {
    initialize_params {
      image_id = data.yandex_compute_image.ubuntu.id
      size     = 10
    }
  }

  secondary_disk {
    disk_id = yandex_compute_disk.mongo_vol.id
  }

  network_interface {
    subnet_id          = var.subnet_id
    nat                = true
    security_group_ids = [var.security_group_id]
  }

  metadata = {
    ssh-keys = "ubuntu:${var.ssh_public_key}"
  }

  allow_stopping_for_update = true
}
