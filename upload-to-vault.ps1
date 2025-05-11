# Установка переменных окружения Vault
$env:VAULT_ADDR = "http://127.0.0.1:8200"
$env:VAULT_TOKEN = ""

# Проверка доступности Vault
Write-Host "Checking Vault status..."
vault status

# Загрузка секретов в Vault
Write-Host "Loading secrets into Vault..."
vault kv put secret/task-manager-bot `
  mongo.host="" `
  mongo.port="" `
  mongo.database="" `
  mongo.username="" `
  mongo.password="" `
  telegram.bot.token="" `
  telegram.bot.username="" `
  superadmin.telegramId="" `
  superadmin.username="" `
  superadmin.firstName="" `
  superadmin.lastName="" `
  superadmin.password=""

Write-Host "Secrets loaded."
