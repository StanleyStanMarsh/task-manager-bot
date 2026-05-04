#!/usr/bin/env bash
# Usage: OPENSTACK_ENV=/path/to/rc STACK_NAME=my-stack ./stack_delete.sh
# Deletes Heat stack after sourcing non-interactive OpenStack credentials.
set -euo pipefail

: "${OPENSTACK_ENV:?}"
: "${STACK_NAME:?}"

set -a
# shellcheck source=/dev/null
source "${OPENSTACK_ENV}"
set +a

if ! openstack stack show "${STACK_NAME}" &>/dev/null; then
  echo "Stack not found: ${STACK_NAME}" >&2
  exit 1
fi

echo "Deleting stack ${STACK_NAME} ..."
openstack stack delete "${STACK_NAME}" --wait
echo "Deleted."
