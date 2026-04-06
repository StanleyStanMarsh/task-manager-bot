output "server_public_ip" {
  description = "Public IP address"
  value       = yandex_compute_instance.bot_server.network_interface[0].nat_ip_address
}

output "ssh_command" {
  value = "ssh ubuntu@${yandex_compute_instance.bot_server.network_interface[0].nat_ip_address}"
}

output "server_name" {
  value = yandex_compute_instance.bot_server.name
}
