#!/usr/bin/env bash
# Jenkins: OPENSTACK_ENV, BOT_ENV_FILE, STACK_NAME, SSH_PRIVATE_KEY, HEAT_ENV_FILE, SSH_USER.
# Если задан JUMP_HOST — openstack/heat и деплой на целевую ВМ выполняются НА jump-машине (доступ к API и student-net).
# Иначе — как раньше: всё с агента Jenkins (нужен прямой доступ к OS_AUTH_URL и к IP новой ВМ).
set -euxo pipefail

: "${OPENSTACK_ENV:?}"
: "${BOT_ENV_FILE:?}"
: "${STACK_NAME:?}"
: "${SSH_PRIVATE_KEY:?}"
: "${HEAT_ENV_FILE:?}"
: "${SSH_USER:=ubuntu}"

: "${JUMP_USER:=ubuntu}"
: "${TARGET_SSH_KEY_ON_JUMP:=/home/ubuntu/.ssh/astafyev-key.pem}"

WS="${WORKSPACE:-$(pwd)}"
cd "$WS"

TEMPLATE="$WS/infra/template.yaml"
ENV_ABS="$WS/$HEAT_ENV_FILE"
REMOTE_DIR="/opt/task-manager-bot"

echo ">>> [deploy] workspace=${WS}"
echo ">>> [deploy] JUMP_HOST=${JUMP_HOST:-<empty>=локальный openstack}"

# ---------------------------------------------------------------------------
# Режим через jump-хост (существующая ВМ в OpenStack с openstack CLI и маршрутом к API)
# ---------------------------------------------------------------------------
if [[ -n "${JUMP_HOST:-}" ]]; then
  echo ">>> [deploy] режим: передача пакета на ${JUMP_USER}@${JUMP_HOST}, Heat/деплой выполняются там"

  STAGE="$(mktemp -d)"
  cleanup_stage() { rm -rf "${STAGE}"; }
  trap cleanup_stage EXIT

  mkdir -p "${STAGE}/infra" "${STAGE}/target"
  cp "${WS}/infra/template.yaml" "${STAGE}/infra/"
  cp "${ENV_ABS}" "${STAGE}/infra/heat-env.yaml"
  cp "${OPENSTACK_ENV}" "${STAGE}/openstack.rc"
  chmod 600 "${STAGE}/openstack.rc"
  cp "${WS}/infra/environment.sh" "${STAGE}/infra/"
  cp "${WS}/docker-compose.yml" "${WS}/Dockerfile" "${STAGE}/"
  cp "${BOT_ENV_FILE}" "${STAGE}/.env"
  JAR="$(ls "${WS}"/target/task-manager-bot-*.jar | head -1)"
  cp "${JAR}" "${STAGE}/target/task-manager-bot-0.5-DEMO.jar"
  cp "${WS}/infra/scripts/remote-heat-deploy-on-jump.sh" "${STAGE}/run-on-jump.sh"
  chmod +x "${STAGE}/run-on-jump.sh"

  JUMP_SSH=(ssh -i "${SSH_PRIVATE_KEY}" -o ConnectTimeout=30 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
  REMOTE_BASE="/tmp/jenkins-heat-${BUILD_NUMBER:-0}-${RANDOM}"

  "${JUMP_SSH[@]}" -o ConnectTimeout=15 "${JUMP_USER}@${JUMP_HOST}" "rm -rf '${REMOTE_BASE}' && mkdir -p '${REMOTE_BASE}'"

  tar -C "${STAGE}" -czf - . | "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "tar xzf - -C '${REMOTE_BASE}'"

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" \
    "bash '${REMOTE_BASE}/run-on-jump.sh' '${REMOTE_BASE}' '${STACK_NAME}' '${TARGET_SSH_KEY_ON_JUMP}' '${SSH_USER}' '${SSH_READY_TIMEOUT_SEC:-600}'"

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "rm -rf '${REMOTE_BASE}'"

  echo ">>> [deploy] jump-режим завершён"
  exit 0
fi

# ---------------------------------------------------------------------------
# Локальный режим (openstack на агенте Jenkins)
# ---------------------------------------------------------------------------
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
"${SCP_BASE[@]}" "${WS}/infra/environment.sh" "${SSH_USER}@${SERVER_IP}:/tmp/environment.sh"
"${SCP_BASE[@]}" "${WS}/docker-compose.yml" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/docker-compose.yml"
"${SCP_BASE[@]}" "${WS}/Dockerfile" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/Dockerfile"
"${SCP_BASE[@]}" "${BOT_ENV_FILE}" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/.env"

JAR="$(ls "${WS}"/target/task-manager-bot-*.jar | head -1)"
"${SCP_BASE[@]}" "${JAR}" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/target/task-manager-bot-0.5-DEMO.jar"

echo ">>> [deploy] run environment.sh on VM"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "sudo bash /tmp/environment.sh"

echo ">>> [deploy] docker compose up"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "cd '${REMOTE_DIR}' && sudo docker compose pull --ignore-pull-failures 2>/dev/null || true"
"${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "cd '${REMOTE_DIR}' && sudo docker compose up -d --build"

echo ">>> [deploy] done. App: http://${SERVER_IP}:8080 (VPN / same L2)"
