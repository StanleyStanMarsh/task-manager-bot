#!/usr/bin/env bash

set -euxo pipefail

BASE_DIR="${1:?}"
STACK_NAME="${2:?}"
TARGET_KEY="${3:?}"
TARGET_USER="${4:-ubuntu}"
SSH_READY_TIMEOUT_SEC="${5:-600}"

REMOTE_APP="/opt/task-manager-bot"

cd "${BASE_DIR}"

echo ">>> [jump] BASE_DIR=${BASE_DIR} STACK=${STACK_NAME}"

set -a
# shellcheck source=/dev/null
source "${BASE_DIR}/openstack.rc"
set +a

echo ">>> [jump] openstack token issue"
openstack token issue >/dev/null

TEMPLATE="${BASE_DIR}/infra/template.yaml"
ENVFILE="${BASE_DIR}/infra/heat-env.yaml"

if openstack stack show "${STACK_NAME}" &>/dev/null; then
  echo ">>> [jump] Heat stack update: ${STACK_NAME}"
  openstack stack update "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENVFILE}" --wait >&2
else
  echo ">>> [jump] Heat stack create: ${STACK_NAME}"
  openstack stack create "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENVFILE}" --wait >&2
fi

echo ">>> [jump] read server_private_ip"
SERVER_IP="$(
  openstack stack output show "${STACK_NAME}" server_private_ip 2>/dev/null | grep -oE '\b([0-9]{1,3}\.){3}[0-9]{1,3}\b' | head -1 || true
)"
if [[ -z "${SERVER_IP}" ]]; then
  echo "Empty server_private_ip output" >&2
  exit 1
fi
echo ">>> [jump] Target VM: ${SERVER_IP}"

SSH_BASE=(ssh -i "${TARGET_KEY}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
SCP_BASE=(scp -i "${TARGET_KEY}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)

echo ">>> [jump] wait for SSH to new VM"
deadline=$((SECONDS + SSH_READY_TIMEOUT_SEC))
until "${SSH_BASE[@]}" -o ConnectTimeout=10 "${TARGET_USER}@${SERVER_IP}" "echo ssh_ready"; do
  if (( SECONDS > deadline )); then
    echo "SSH to target VM not ready before timeout" >&2
    exit 1
  fi
  sleep 10
done

echo ">>> [jump] wait cloud-init (best effort)"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" "command -v cloud-init >/dev/null 2>&1 && (cloud-init status --wait || true) || true"

echo ">>> [jump] prepare dir on target"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" "sudo mkdir -p '${REMOTE_APP}/target' && sudo chown -R '${TARGET_USER}:${TARGET_USER}' '${REMOTE_APP}'"

echo ">>> [jump] scp to target"
"${SCP_BASE[@]}" "${BASE_DIR}/docker-compose.yml" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/docker-compose.yml"
"${SCP_BASE[@]}" "${BASE_DIR}/Dockerfile" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/Dockerfile"
"${SCP_BASE[@]}" "${BASE_DIR}/.env" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/.env"
"${SCP_BASE[@]}" "${BASE_DIR}/target/task-manager-bot-0.5-DEMO.jar" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/target/task-manager-bot-0.5-DEMO.jar"

echo ">>> [jump] docker compose up"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" bash -s <<EOS
set -euxo pipefail
cd ${REMOTE_APP}
if docker compose version >/dev/null 2>&1; then
  docker compose pull --ignore-pull-failures 2>/dev/null || true
  docker compose up -d --build
elif command -v sudo >/dev/null 2>&1 && sudo docker compose version >/dev/null 2>&1; then
  sudo docker compose pull --ignore-pull-failures 2>/dev/null || true
  sudo docker compose up -d --build
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose pull --ignore-pull-failures 2>/dev/null || true
  docker-compose up -d --build
elif command -v sudo >/dev/null 2>&1 && sudo docker-compose version >/dev/null 2>&1; then
  sudo docker-compose pull --ignore-pull-failures 2>/dev/null || true
  sudo docker-compose up -d --build
else
  echo "docker compose / docker-compose not found" >&2
  exit 127
fi
EOS

echo ">>> [jump] done. App: http://${SERVER_IP}:8080"

rm -f "${BASE_DIR}/openstack.rc"
