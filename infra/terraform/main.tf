data "yandex_compute_image" "ubuntu" {
  family = "ubuntu-2204-lts"
}

data "yandex_vpc_subnet" "subnet" {
  subnet_id = var.subnet_id
}

resource "yandex_container_registry" "registry" {
  name      = "${var.vm_name}-registry"
  folder_id = var.folder_id
}

resource "yandex_compute_instance" "vm" {
  name        = var.vm_name
  platform_id = var.vm_platform_id
  zone        = data.yandex_vpc_subnet.subnet.zone

  resources {
    cores         = var.vm_cores
    memory        = var.vm_memory_gb
    core_fraction = var.vm_core_fraction
  }

  boot_disk {
    initialize_params {
      image_id = data.yandex_compute_image.ubuntu.id
      size     = var.vm_disk_gb
      type     = "network-ssd"
    }
  }

  network_interface {
    subnet_id          = var.subnet_id
    nat                = true
    security_group_ids = [var.security_group_id]
  }

  metadata = {
    "ssh-keys" = "${var.ssh_username}:${var.ssh_public_key}"
  }
}

