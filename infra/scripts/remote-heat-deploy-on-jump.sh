#!/usr/bin/env bash
# Запускать НА jump-машине (есть доступ к OpenStack API и к приватной сети студента).
# Аргументы: BASE_DIR STACK_NAME TARGET_SSH_KEY_PATH TARGET_USER SSH_READY_TIMEOUT_SEC
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
  openstack stack update "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENVFILE}" --wait
else
  echo ">>> [jump] Heat stack create: ${STACK_NAME}"
  openstack stack create "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENVFILE}" --wait
fi

echo ">>> [jump] read server_private_ip"
SERVER_IP="$(
  openstack stack output show "${STACK_NAME}" server_private_ip -f value -c output_value | tr -d '\r'
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

echo ">>> [jump] prepare dir on target"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" "sudo mkdir -p '${REMOTE_APP}/target' && sudo chown -R '${TARGET_USER}:${TARGET_USER}' '${REMOTE_APP}'"

echo ">>> [jump] scp to target"
"${SCP_BASE[@]}" "${BASE_DIR}/infra/environment.sh" "${TARGET_USER}@${SERVER_IP}:/tmp/environment.sh"
"${SCP_BASE[@]}" "${BASE_DIR}/docker-compose.yml" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/docker-compose.yml"
"${SCP_BASE[@]}" "${BASE_DIR}/Dockerfile" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/Dockerfile"
"${SCP_BASE[@]}" "${BASE_DIR}/.env" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/.env"
"${SCP_BASE[@]}" "${BASE_DIR}/target/task-manager-bot-0.5-DEMO.jar" "${TARGET_USER}@${SERVER_IP}:${REMOTE_APP}/target/task-manager-bot-0.5-DEMO.jar"

echo ">>> [jump] environment.sh on target"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" "sudo bash /tmp/environment.sh"

echo ">>> [jump] docker compose up"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" "cd '${REMOTE_APP}' && sudo docker compose pull --ignore-pull-failures 2>/dev/null || true"
"${SSH_BASE[@]}" "${TARGET_USER}@${SERVER_IP}" "cd '${REMOTE_APP}' && sudo docker compose up -d --build"

echo ">>> [jump] done. App: http://${SERVER_IP}:8080"

rm -f "${BASE_DIR}/openstack.rc"
