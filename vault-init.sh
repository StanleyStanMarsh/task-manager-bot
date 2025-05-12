#!/bin/sh

VAULT_TOKEN=replace_this_text_with_token
export VAULT_TOKEN

echo "Vault is ready. Initializing and loading secrets..."

# Enable key-value storage
vault secrets enable -version=2 -path=secret kv

# Load secrets
vault kv put secret/task-manager-bot \
  mongo.host="" \
  mongo.port="" \
  mongo.database="" \
  mongo.username="" \
  mongo.password="" \
  telegram.bot.token="" \
  telegram.bot.username="" \
  superadmin.telegramId="" \
  superadmin.username="" \
  superadmin.firstName="" \
  superadmin.lastName="" \
  superadmin.password=""

echo "Secrets loaded successfully!"