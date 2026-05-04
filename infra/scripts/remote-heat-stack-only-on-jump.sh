#!/usr/bin/env bash
# Только Heat на jump. В stdout в конце — одна строка: приватный IP ВМ. Остальные сообщения в stderr.
set -euxo pipefail

BASE_DIR="${1:?}"
STACK_NAME="${2:?}"
cd "${BASE_DIR}"

echo ">>> [heat-only] stack=${STACK_NAME}" >&2

set -a
# shellcheck source=/dev/null
source "${BASE_DIR}/openstack.rc"
set +a

openstack token issue >/dev/null

TEMPLATE="${BASE_DIR}/infra/template.yaml"
ENVFILE="${BASE_DIR}/infra/heat-env.yaml"

if openstack stack show "${STACK_NAME}" &>/dev/null; then
  echo ">>> [heat-only] stack update" >&2
  openstack stack update "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENVFILE}" --wait
else
  echo ">>> [heat-only] stack create" >&2
  openstack stack create "${STACK_NAME}" -t "${TEMPLATE}" -e "${ENVFILE}" --wait
fi

SERVER_IP="$(
  openstack stack output show "${STACK_NAME}" server_private_ip -f value -c output_value | tr -d '\r'
)"
if [[ -z "${SERVER_IP}" ]]; then
  echo "Empty server_private_ip" >&2
  exit 1
fi
echo ">>> [heat-only] SERVER_IP=${SERVER_IP}" >&2
echo "${SERVER_IP}"
