#!/usr/bin/env bash
# OPENSTACK_ENV, BOT_ENV_FILE, STACK_NAME, SSH_PRIVATE_KEY, HEAT_ENV_FILE, SSH_USER
# JUMP_HOST: heat на существующей ВМ. Деплой на целевую ВМ:
#   USE_PROXYJUMP_FOR_TARGET=1 (по умолчанию) — ключ только на Jenkins, SSH через jump (без ключа на диске jump)
#   USE_PROXYJUMP_FOR_TARGET=0 — полный сценарий на jump, нужен TARGET_SSH_KEY_ON_JUMP на ВМ
set -euxo pipefail

: "${OPENSTACK_ENV:?}"
: "${BOT_ENV_FILE:?}"
: "${STACK_NAME:?}"
: "${SSH_PRIVATE_KEY:?}"
: "${HEAT_ENV_FILE:?}"
: "${SSH_USER:=ubuntu}"

: "${JUMP_USER:=ubuntu}"
: "${TARGET_SSH_KEY_ON_JUMP:=/home/ubuntu/.ssh/astafyev-key.pem}"
# 1 = ProxyJump с агента Jenkins; 0 = scp/ssh с jump к целевой ВМ
: "${USE_PROXYJUMP_FOR_TARGET:=1}"

WS="${WORKSPACE:-$(pwd)}"
cd "$WS"

TEMPLATE="$WS/infra/template.yaml"
ENV_ABS="$WS/$HEAT_ENV_FILE"
REMOTE_DIR="/opt/task-manager-bot"

echo ">>> [deploy] workspace=${WS}"
echo ">>> [deploy] JUMP_HOST=${JUMP_HOST:-<empty>}"
echo ">>> [deploy] USE_PROXYJUMP_FOR_TARGET=${USE_PROXYJUMP_FOR_TARGET}"

# --- Общий деплой на целевую ВМ (после того как известен SERVER_IP) ---
run_deploy_on_target() {
  : "${SERVER_IP:?}"
  local deadline
  local SUDO
  SUDO=""
  echo ">>> [deploy] wait for SSH to ${SERVER_IP}"
  deadline=$((SECONDS + ${SSH_READY_TIMEOUT_SEC:-600}))
  until "${SSH_BASE[@]}" -o ConnectTimeout=10 "${SSH_USER}@${SERVER_IP}" "echo ssh_ready"; do
    if (( SECONDS > deadline )); then
      echo "SSH not ready before timeout" >&2
      exit 1
    fi
    sleep 10
  done

  # Дожидаемся cloud-init (Heat user_data) — иначе Docker/sudo/директории могут быть ещё не готовы.
  # cloud-init status --wait может отсутствовать в очень старых образах, поэтому делаем best-effort.
  echo ">>> [deploy] wait cloud-init (best effort)"
  "${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "command -v cloud-init >/dev/null 2>&1 && (cloud-init status --wait || true) || true"

  # Определяем sudo после cloud-init (его ставим в packages)
  if "${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "command -v sudo >/dev/null 2>&1"; then
    SUDO="sudo"
  fi

  echo ">>> [deploy] prepare remote dir"
  "${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "${SUDO} mkdir -p '${REMOTE_DIR}/target' && ${SUDO} chown -R '${SSH_USER}:${SSH_USER}' '${REMOTE_DIR}'"

  echo ">>> [deploy] scp files"
  "${SCP_BASE[@]}" "${WS}/docker-compose.yml" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/docker-compose.yml"
  "${SCP_BASE[@]}" "${WS}/Dockerfile" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/Dockerfile"
  "${SCP_BASE[@]}" "${BOT_ENV_FILE}" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/.env"

  local jar
  jar="$(ls "${WS}"/target/task-manager-bot-*.jar | head -1)"
  "${SCP_BASE[@]}" "${jar}" "${SSH_USER}@${SERVER_IP}:${REMOTE_DIR}/target/task-manager-bot-0.5-DEMO.jar"

  echo ">>> [deploy] docker compose up"
  "${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "cd '${REMOTE_DIR}' && (docker compose pull --ignore-pull-failures 2>/dev/null || ${SUDO} docker compose pull --ignore-pull-failures 2>/dev/null || true)"
  "${SSH_BASE[@]}" "${SSH_USER}@${SERVER_IP}" "cd '${REMOTE_DIR}' && (docker compose up -d --build || ${SUDO} docker compose up -d --build)"

  echo ">>> [deploy] done. App: http://${SERVER_IP}:8080"
}

# ---------------------------------------------------------------------------
# Jump + ProxyJump: Heat на jump, деплой с Jenkins (ключ не хранится на jump)
# ---------------------------------------------------------------------------
if [[ -n "${JUMP_HOST:-}" && "${USE_PROXYJUMP_FOR_TARGET}" == "1" ]]; then
  echo ">>> [deploy] режим: Heat на jump, деплой через ProxyJump с агента (ключ только здесь)"

  STAGE="$(mktemp -d)"
  cleanup_stage() { rm -rf "${STAGE}"; }
  trap cleanup_stage EXIT

  mkdir -p "${STAGE}/infra"
  cp "${WS}/infra/template.yaml" "${STAGE}/infra/"
  cp "${ENV_ABS}" "${STAGE}/infra/heat-env.yaml"
  cp "${OPENSTACK_ENV}" "${STAGE}/openstack.rc"
  chmod 600 "${STAGE}/openstack.rc"
  cp "${WS}/infra/scripts/remote-heat-stack-only-on-jump.sh" "${STAGE}/heat-only.sh"
  chmod +x "${STAGE}/heat-only.sh"

  JUMP_SSH=(ssh -i "${SSH_PRIVATE_KEY}" -o ConnectTimeout=30 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
  REMOTE_BASE="/tmp/jenkins-heat-${BUILD_NUMBER:-0}-${RANDOM}"

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "rm -rf '${REMOTE_BASE}' && mkdir -p '${REMOTE_BASE}'"

  tar -C "${STAGE}" -czf - . | "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "tar xzf - -C '${REMOTE_BASE}'"

  SERVER_IP="$(
    "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" \
      "bash '${REMOTE_BASE}/heat-only.sh' '${REMOTE_BASE}' '${STACK_NAME}'" | tail -n1 | tr -d '\r\n'
  )"

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "rm -rf '${REMOTE_BASE}'"

  if [[ -z "${SERVER_IP}" ]]; then
    echo "Could not read SERVER_IP from heat-only script" >&2
    exit 1
  fi
  echo ">>> [deploy] SERVER_IP=${SERVER_IP}"

  # ProxyJump часто не передаёт -i на bastion → Permission denied на первом hop.
  # Явный ProxyCommand: к jump с тем же ключом, затем туннель к целевой ВМ.
  _PX=(ssh -q -W "%h:%p" -i "${SSH_PRIVATE_KEY}" -o ConnectTimeout=30 -o IdentitiesOnly=yes
    -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null
    "${JUMP_USER}@${JUMP_HOST}")
  _PX_CMD="${_PX[*]}"
  SSH_BASE=(
    ssh
    -o "ProxyCommand=${_PX_CMD}"
    -i "${SSH_PRIVATE_KEY}"
    -o IdentitiesOnly=yes
    -o ConnectTimeout=30
    -o StrictHostKeyChecking=accept-new
    -o UserKnownHostsFile=/dev/null
  )
  SCP_BASE=(
    scp
    -o "ProxyCommand=${_PX_CMD}"
    -i "${SSH_PRIVATE_KEY}"
    -o IdentitiesOnly=yes
    -o ConnectTimeout=30
    -o StrictHostKeyChecking=accept-new
    -o UserKnownHostsFile=/dev/null
  )

  run_deploy_on_target
  exit 0
fi

# ---------------------------------------------------------------------------
# Jump без ProxyJump: всё на jump (нужен приватный ключ на jump → целевая ВМ)
# ---------------------------------------------------------------------------
if [[ -n "${JUMP_HOST:-}" ]]; then
  echo ">>> [deploy] режим: Heat и деплой целиком на ${JUMP_USER}@${JUMP_HOST} (нужен ключ TARGET_SSH_KEY_ON_JUMP на jump)"

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

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "rm -rf '${REMOTE_BASE}' && mkdir -p '${REMOTE_BASE}'"

  tar -C "${STAGE}" -czf - . | "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "tar xzf - -C '${REMOTE_BASE}'"

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" \
    "bash '${REMOTE_BASE}/run-on-jump.sh' '${REMOTE_BASE}' '${STACK_NAME}' '${TARGET_SSH_KEY_ON_JUMP}' '${SSH_USER}' '${SSH_READY_TIMEOUT_SEC:-600}'"

  "${JUMP_SSH[@]}" "${JUMP_USER}@${JUMP_HOST}" "rm -rf '${REMOTE_BASE}'"

  echo ">>> [deploy] jump full-режим завершён"
  exit 0
fi

# ---------------------------------------------------------------------------
# Локальный режим (openstack на агенте Jenkins)
# ---------------------------------------------------------------------------
echo ">>> [deploy] локальный openstack: template=${TEMPLATE} env=${ENV_ABS}"

set -a
# shellcheck source=/dev/null
source "${OPENSTACK_ENV}"
set +a

openstack token issue >/dev/null

if openstack stack show "${STACK_NAME}" &>/dev/null; then
  echo ">>> [deploy] Heat stack update: ${STACK_NAME}"
  openstack stack update "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENV_ABS}" --wait
else
  echo ">>> [deploy] Heat stack create: ${STACK_NAME}"
  openstack stack create "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENV_ABS}" --wait
fi

SERVER_IP="$(
  openstack stack output show "${STACK_NAME}" server_private_ip 2>/dev/null | grep -oE '\b([0-9]{1,3}\.){3}[0-9]{1,3}\b' | head -1 || true
)"
if [[ -z "${SERVER_IP}" ]]; then
  echo "Empty server_private_ip output" >&2
  exit 1
fi
echo ">>> [deploy] Target VM: ${SERVER_IP}"

SSH_BASE=(ssh -i "${SSH_PRIVATE_KEY}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
SCP_BASE=(scp -i "${SSH_PRIVATE_KEY}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)

run_deploy_on_target
