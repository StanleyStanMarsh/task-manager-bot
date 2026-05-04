#!/usr/bin/env bash
# Run from the same environment as Jenkins jobs (host with VPN, Jenkins container, or agent).
# Usage: OPENSTACK_ENV=/path/to/noninteractive-openrc.sh ./verify-openstack-connectivity.sh [optional_private_ip_to_ping]
set -euo pipefail

if [[ -z "${OPENSTACK_ENV:-}" ]]; then
  echo "Set OPENSTACK_ENV to a non-interactive openrc file (source-able exports)." >&2
  exit 1
fi

# shellcheck source=/dev/null
set -a
source "${OPENSTACK_ENV}"
set +a

echo "==> openstack token issue"
openstack token issue >/dev/null
echo "OK: Keystone reachable."

if [[ -n "${1:-}" ]]; then
  echo "==> ping/curl smoke to $1 (optional; may fail if ICMP blocked)"
  ping -c1 -W2 "$1" 2>/dev/null || echo "Note: ping failed (ICMP may be blocked); try SSH manually."
fi

echo "Done. If this fails inside the Jenkins container but works on the host, use host networking,"
echo "a Jenkins agent on the OpenStack jump VM, or run OpenStack/SSH steps on a host-agent label."
