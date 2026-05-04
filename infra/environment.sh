#!/bin/bash

set -euo pipefail

log() {
    echo "[environment] $*" >&2
}

# Проверка прав root
# Скрипт должен запускаться с правами root
if [[ $EUID -ne 0 ]]; then
    echo "Run as root: sudo $0" >&2
    exit 1
fi

log "Start environment setup"

# 1. Установка базовых пакетов
log "Install base packages"

export DEBIAN_FRONTEND=noninteractive

apt-get update -qq

apt-get install -y -qq \
    curl wget git jq unzip \
    gnupg ca-certificates \
    apt-transport-https \
    software-properties-common \
    lsb-release \
    xfsprogs e2fsprogs

# 2. Установка Docker
log "Install Docker"

# удаляем старые версии docker если они есть
apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

# добавляем официальный docker репозиторий
install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
gpg --dearmor -o /etc/apt/keyrings/docker.gpg

chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -qq

# установка docker engine и compose plugin
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin

# запуск docker
systemctl enable --now docker

log "Docker: $(docker --version)"
log "Docker Compose: $(docker compose version)"

# 3. Установка Java 23
log "Install Java 23"

JAVA_VERSION="23.0.2+7"
JAVA_BUILD="23.0.2+7"

JAVA_FILENAME="OpenJDK23U-jdk_x64_linux_hotspot_${JAVA_BUILD}.tar.gz"

JAVA_URL="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-${JAVA_VERSION}/${JAVA_FILENAME}"

JAVA_INSTALL_DIR="/opt/java/temurin-23"

mkdir -p "${JAVA_INSTALL_DIR}"

if [[ ! -f "${JAVA_INSTALL_DIR}/bin/java" ]]; then

    log "Download Java"

    wget --progress=bar:force \
        -O "/tmp/${JAVA_FILENAME}" \
        "${JAVA_URL}"

    log "Extract Java"

    tar -xzf "/tmp/${JAVA_FILENAME}" \
        -C "${JAVA_INSTALL_DIR}" \
        --strip-components=1

    rm -f "/tmp/${JAVA_FILENAME}"

    # переменные окружения
    echo "JAVA_HOME=${JAVA_INSTALL_DIR}" >> /etc/environment
    echo "PATH=\${JAVA_HOME}/bin:\${PATH}" >> /etc/environment

    export JAVA_HOME="${JAVA_INSTALL_DIR}"

    log "Java installed to ${JAVA_INSTALL_DIR}"

else
    log "Java already installed"
fi

log "Java: $(${JAVA_INSTALL_DIR}/bin/java -version 2>&1 | head -1)"

# 4. Монтирование Cinder-тома для MongoDB
log "Configure MongoDB volume"

mount_volume() {

    local device="$1"
    local mount_point="$2"
    local owner="$3"
    local group="$4"

    log "Check device ${device}"

    # если устройство отсутствует — пропускаем
    if [[ ! -b "${device}" ]]; then
        log "Device not found: ${device}"
        return 0
    fi

    # если уже смонтировано
    if mountpoint -q "${mount_point}" 2>/dev/null; then
        log "Already mounted: ${mount_point}"
        return 0
    fi

    # проверяем файловую систему
    if ! blkid "${device}" | grep -q "TYPE="; then
        log "Format ${device} as ext4"
        mkfs.ext4 -F "${device}"
    else
        log "Filesystem already exists"
    fi

    mkdir -p "${mount_point}"

    mount "${device}" "${mount_point}"

    log "Mounted ${device} -> ${mount_point}"

    # добавляем в fstab
    if ! grep -q "${device}" /etc/fstab; then
        echo "${device} ${mount_point} ext4 defaults,nofail 0 2" >> /etc/fstab
    fi

    # создаём пользователя mongodb если его нет
    if ! id -u "${owner}" &>/dev/null; then
        useradd -r -s /usr/sbin/nologin "${owner}" 2>/dev/null || true
    fi

    chown -R "${owner}:${group}" "${mount_point}"
    chmod 750 "${mount_point}"

    log "Permissions configured for ${mount_point}"
}

# единственный том — для MongoDB
mount_volume "/dev/vdb" "/var/lib/mongodb" "mongodb" "mongodb"

# 5. Пользователь для работы с Docker
log "Configure docker user"

if ! id -u botuser &>/dev/null; then
    useradd -m -s /bin/bash botuser
fi

# добавляем пользователя в группу docker
usermod -aG docker botuser

# копируем SSH ключи ubuntu -> botuser
if [[ -d /home/ubuntu/.ssh ]]; then

    mkdir -p /home/botuser/.ssh

    cp -r /home/ubuntu/.ssh/* /home/botuser/.ssh/ 2>/dev/null || true

    chmod 700 /home/botuser/.ssh
    chmod 600 /home/botuser/.ssh/* 2>/dev/null || true

    chown -R botuser:botuser /home/botuser/.ssh
fi

log "User botuser added to docker group"

# 6. Директория проекта
log "Prepare project directory"

APP_DIR="/opt/task-manager-bot"

mkdir -p "${APP_DIR}"

chown -R botuser:botuser "${APP_DIR}"

log "Environment setup finished"
log "Project directory: ${APP_DIR}"