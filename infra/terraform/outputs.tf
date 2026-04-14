output "vm_external_ip" {
  value       = yandex_compute_instance.vm.network_interface[0].nat_ip_address
  description = "Public IPv4 of deployed VM"
}

output "ssh_username" {
  value       = var.ssh_username
  description = "SSH username"
}