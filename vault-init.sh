#!/bin/sh

export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN

sleep 3

vault secrets enable -version=2 -path=secret kv || true

vault kv put secret/task-manager-bot \
  mongo.host="mongo" \
  mongo.port="27017" \
  mongo.database="mydb" \
  mongo.username="$MONGO_USERNAME" \
  mongo.password="$MONGO_PASSWORD" \
  telegram.bot.token="$TELEGRAM_BOT_TOKEN" \
  telegram.bot.username="$TELEGRAM_BOT_USERNAME" \
  superadmin.telegramId="123456789" \
  superadmin.username="super_admin" \
  superadmin.firstName="admin" \
  superadmin.lastName="admin" \
  superadmin.password="password"

echo "Secrets loaded!"
