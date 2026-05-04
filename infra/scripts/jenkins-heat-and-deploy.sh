#!/usr/bin/env bash
# Jenkins: env OPENSTACK_ENV, BOT_ENV_FILE, STACK_NAME, SSH_PRIVATE_KEY, HEAT_ENV_FILE, SSH_USER.
# Корень checkout = репозиторий task-manager-bot (рядом с pom.xml).
set -euxo pipefail

: "${OPENSTACK_ENV:?}"
: "${BOT_ENV_FILE:?}"
: "${STACK_NAME:?}"
: "${SSH_PRIVATE_KEY:?}"
: "${HEAT_ENV_FILE:?}"
: "${SSH_USER:=ubuntu}"

WS="${WORKSPACE:-$(pwd)}"
cd "$WS"

TEMPLATE="$WS/infra/template.yaml"
ENV_ABS="$WS/$HEAT_ENV_FILE"
REMOTE_DIR="/opt/task-manager-bot"

echo ">>> [deploy] workspace=${WS}"
echo ">>> [deploy] template=${TEMPLATE} env=${ENV_ABS}"

set -a
# shellcheck source=/dev/null
source "${OPENSTACK_ENV}"
set +a

echo ">>> [deploy] openstack token issue"
openstack token issue >/dev/null

if openstack stack show "${STACK_NAME}" &>/dev/null; then
  echo ">>> [deploy] Heat stack update: ${STACK_NAME}"
  openstack stack update "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENV_ABS}" --wait
else
  echo ">>> [deploy] Heat stack create: ${STACK_NAME}"
  openstack stack create "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENV_ABS}" --wait
fi

echo ">>> [deploy] read stack output server_private_ip"
SERVER_IP="$(
  openstack stack output show "${STACK_NAME}" server_private_ip -f value -c output_value | tr -d '\r'
)"
if [[ -z "${SERVER_IP}" ]]; then
  echo "Empty server_private_ip output" >&2
  exit 1
fi
echo ">>> [deploy] Target VM: ${SERVER_IP}"

SSH_BASE=(ssh -i "${SSH_PRIVATE_KEY}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
SCP_BASE=(scp -i "${SSH_PRIVATE_KEY}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)

echo ">>> [deploy] wait for SSH"
deadline=$((SECONDS + ${SSH_READY_TIMEOUT_SEC:-600}))
until "${SSH_BASE[@]}" -o ConnectTimeout=10 "${SSH_USER}@${SERVER_IP}" "echo ssh_ready"; do
  if (( SECONDS > deadline )); then
    echo "SSH not ready before timeout" >&2
    exit 1
  fi
  sleep 10
done

echo ">>> [deploy] prepare remote dir"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "sudo mkdir -p '${REMOTE_DIR}/target' && sudo chown -R '${SSH_USER}:${SSH_USER}' '${REMOTE_DIR}'"

echo ">>> [deploy] scp files"
"${SCP_BASE[@]}" "$WS/infra/environment.sh" "${SSH_USER}@${SERVER_IP}:/tmp/environment.sh"
"${SCP_BASE[@]}" "$WS/docker-compose.yml" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/docker-compose.yml"
"${SCP_BASE[@]}" "$WS/Dockerfile" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/Dockerfile"
"${SCP_BASE[@]}" "${BOT_ENV_FILE}" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/.env"

JAR="$(ls "$WS"/target/task-manager-bot-*.jar | head -1)"
"${SCP_BASE[@]}" "${JAR}" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/target/task-manager-bot-0.5-DEMO.jar"

echo ">>> [deploy] run environment.sh on VM"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "sudo bash /tmp/environment.sh"

echo ">>> [deploy] docker compose up"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "cd '${REMOTE_DIR}' && sudo docker compose pull --ignore-pull-failures 2>/dev/null || true"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "cd '${REMOTE_DIR}' && sudo docker compose up -d --build"

echo ">>> [deploy] done. App: http://${SERVER_IP}:8080 (VPN / same L2)"
