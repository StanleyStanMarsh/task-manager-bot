pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    skipDefaultCheckout(true)
    disableConcurrentBuilds()
  }

  parameters {
    booleanParam(
      name: 'SKIP_K8S_DEPLOY',
      defaultValue: false,
      description: 'Только JAR и Docker-образ, без kubectl.'
    )
    string(
      name: 'MINIKUBE_PROFILE',
      defaultValue: 'minikube',
      description: 'Профиль minikube (как --name у kind).'
    )
  }

  environment {
    PROJECT_DIR = '.'
    JENKINS_CONTAINER = 'jenkins-lab'
    K8S_DIR = 'k8s'
    NAMESPACE = 'task-manager-bot'
    IMAGE_NAME = 'task-manager-bot:latest'
    MINIKUBE_HOME = '/var/jenkins_home/.minikube-host'
  }

  stages {
    stage('Checkout') {
      steps {
        cleanWs(deleteDirs: true, disableDeferredWipeout: true)
        checkout scm
      }
    }

    stage('Build JAR') {
      steps {
        sh '''
          set -euo pipefail
          export PATH="/usr/local/bin:/usr/bin:/bin:${PATH}"
          docker run --rm \
            --volumes-from "${JENKINS_CONTAINER}" \
            -w "${WORKSPACE}/${PROJECT_DIR}" \
            maven:3.9.9-eclipse-temurin-23 \
            mvn -B -DskipTests package
        '''
      }
      post {
        success {
          archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }
      }
    }

    stage('Docker build & load to minikube') {
      steps {
        sh """
          set -euo pipefail
          export PATH="/usr/local/bin:/usr/bin:/bin:\${PATH}"
          docker build --platform linux/arm64 -t ${env.IMAGE_NAME} .

          PROFILE='${params.MINIKUBE_PROFILE}'
          MNODE=\$(docker ps --filter "label=name.minikube.sigs.k8s.io=\${PROFILE}" --format '{{.Names}}' | head -n1)
          if [ -z "\${MNODE}" ]; then
            MNODE=\$(docker ps --format '{{.Names}}' | grep -E "^\${PROFILE}\$|^\${PROFILE}-" | head -n1)
          fi
          if [ -z "\${MNODE}" ]; then
            echo "Не найден контейнер узла minikube (профиль \${PROFILE})." >&2
            docker ps -a --format 'table {{.Names}}\\t{{.Image}}' | head -25 >&2 || true
            exit 1
          fi
          echo "Узел minikube (контейнер): \${MNODE}"
          docker save ${env.IMAGE_NAME} | docker exec -i "\${MNODE}" docker load
        """
      }
    }

    stage('Deploy to K8s') {
      when {
        expression { return !params.SKIP_K8S_DEPLOY }
      }
      steps {
        script {
          echo '📡 kubectl + манифесты...'
          withEnv(["MINIKUBE_PROFILE=${params.MINIKUBE_PROFILE}"]) {
            sh '''
              set -euo pipefail
              export PATH="/usr/local/bin:/usr/bin:/bin:${PATH}"
              command -v kubectl >/dev/null 2>&1 || { echo "kubectl не найден. Установите kubectl в образ Jenkins."; exit 1; }

              # Поиск kubeconfig
              if [ -n "${KUBECONFIG:-}" ] && [ -f "${KUBECONFIG}" ]; then
                :
              elif [ -f /var/jenkins_home/.kube-host/config ]; then
                export KUBECONFIG=/var/jenkins_home/.kube-host/config
              elif [ -f /var/jenkins_home/.kube/config ]; then
                export KUBECONFIG=/var/jenkins_home/.kube/config
              else
                echo "Нет kubeconfig. Смонтируйте ~/.kube в docker-compose." >&2
                exit 1
              fi
              
              # Исправление путей в kubeconfig для работы в контейнере
              KCFG_FIX="${WORKSPACE}/.kubeconfig-pathfix"
              
              # Многоэтапная замена путей с правильным экранированием
              cat "${KUBECONFIG}" | \
                sed -e 's|C:\\\\Users\\\\[^\\\\]*\\\\[.]minikube|/var/jenkins_home/.minikube-host|g' \
                    -e 's|C:/Users/[^/]*/[.]minikube|/var/jenkins_home/.minikube-host|g' \
                    -e 's|/Users/[^/]*/[.]minikube|/var/jenkins_home/.minikube-host|g' \
                    -e 's|\\\\|/|g' \
                    -e 's|C:/|/|g' \
                    -e 's|//|/|g' \
                > "${KCFG_FIX}"
              
              echo "=== Оригинальный kubeconfig (первые 20 строк) ==="
              head -20 "${KUBECONFIG}"
              echo ""
              echo "=== Исправленный kubeconfig (первые 20 строк) ==="
              head -20 "${KCFG_FIX}"
              
              export KUBECONFIG="${KCFG_FIX}"

              # Установка контекста
              echo "Переключение на контекст: ${MINIKUBE_PROFILE}"
              kubectl config use-context "${MINIKUBE_PROFILE}" || {
                echo "Не удалось переключиться на контекст ${MINIKUBE_PROFILE}"
                echo "Доступные контексты:"
                kubectl config get-contexts
                exit 1
              }
              
              # Исправление server URL для доступа из контейнера
              CLUSTER_NAME="$(kubectl config view --minify -o jsonpath='{.clusters[0].name}' 2>/dev/null || true)"
              SERVER="$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}' 2>/dev/null || true)"
              
              if echo "${SERVER}" | grep -Eq '^https://127[.]0[.]0[.]1:[0-9]+$' && [ -n "${CLUSTER_NAME}" ]; then
                PORT="${SERVER##*:}"
                echo "Замена server URL для доступа из контейнера: ${SERVER} -> https://host.docker.internal:${PORT}"
                kubectl config set-cluster "${CLUSTER_NAME}" \
                  --server="https://host.docker.internal:${PORT}" \
                  --insecure-skip-tls-verify=true >/dev/null
              fi

              # Проверка существования сертификатов
              echo "Проверка путей к сертификатам..."
              CERT_PATH="$(kubectl config view --minify -o jsonpath='{.users[0].user.client-certificate}' 2>/dev/null || true)"
              if [ -n "${CERT_PATH}" ]; then
                echo "Путь к сертификату в конфиге: ${CERT_PATH}"
                if [ ! -f "${CERT_PATH}" ]; then
                  echo "⚠️  Сертификат не найден по пути: ${CERT_PATH}"
                  # Попытка найти сертификат
                  CERT_NAME=$(basename "${CERT_PATH}" 2>/dev/null || echo "")
                  if [ -n "${CERT_NAME}" ]; then
                    # Ищем сертификат в разных местах
                    for SEARCH_PATH in "/var/jenkins_home/.minikube-host/" "/var/jenkins_home/.minikube-host/profiles/${MINIKUBE_PROFILE}/"; do
                      if [ -f "${SEARCH_PATH}${CERT_NAME}" ]; then
                        echo "✅ Найден сертификат: ${SEARCH_PATH}${CERT_NAME}"
                        # Обновляем путь в конфиге
                        USER_NAME="$(kubectl config view --minify -o jsonpath='{.users[0].name}' 2>/dev/null || true)"
                        if [ -n "${USER_NAME}" ]; then
                          kubectl config set-credentials "${USER_NAME}" --client-certificate="${SEARCH_PATH}${CERT_NAME}" --embed-certs=true
                        fi
                        break
                      fi
                    done
                  fi
                else
                  echo "✅ Сертификат найден: ${CERT_PATH}"
                fi
              fi

              # Создание финального конфига с встроенными сертификатами
              echo "Создание финального kubeconfig с встроенными сертификатами..."
              kubectl config view --flatten --embed-certs=true > "${WORKSPACE}/.kubeconfig-run"
              export KUBECONFIG="${WORKSPACE}/.kubeconfig-run"
              
              echo "✅ Kubeconfig подготовлен"
            '''
          }

          sh """
            set -euo pipefail
            export PATH="/usr/local/bin:/usr/bin:/bin:\${PATH}"
            export KUBECONFIG="${WORKSPACE}/.kubeconfig-run"
            kubectl config use-context ${params.MINIKUBE_PROFILE}
            echo "Проверка подключения к кластеру:"
            kubectl cluster-info
            kubectl get nodes
          """

          withCredentials([
            file(credentialsId: 'VAULT_INIT_SH', variable: 'CRED_VAULT_INIT_SH'),
            string(credentialsId: 'VAULT_DEV_ROOT_TOKEN_ID', variable: 'VAULT_DEV_ROOT_TOKEN_ID'),
            string(credentialsId: 'MONGO_INITDB_ROOT_USERNAME', variable: 'MONGO_INITDB_ROOT_USERNAME'),
            string(credentialsId: 'MONGO_INITDB_ROOT_PASSWORD', variable: 'MONGO_INITDB_ROOT_PASSWORD')
          ]) {
            sh '''
              set -euo pipefail
              export PATH="/usr/local/bin:/usr/bin:/bin:${PATH}"
              export KUBECONFIG="${WORKSPACE}/.kubeconfig-run"

              NS="${NAMESPACE}"
              cp "${CRED_VAULT_INIT_SH}" "${WORKSPACE}/.vault-init-from-jenkins.sh"
              chmod 0755 "${WORKSPACE}/.vault-init-from-jenkins.sh"

              kubectl apply -f "${K8S_DIR}/namespace.yaml"
              kubectl -n "${NS}" create secret generic mongo-creds \
                --from-literal=username="${MONGO_INITDB_ROOT_USERNAME}" \
                --from-literal=password="${MONGO_INITDB_ROOT_PASSWORD}" \
                --dry-run=client -o yaml | kubectl apply -f -
              kubectl -n "${NS}" create secret generic vault-root \
                --from-literal=token="${VAULT_DEV_ROOT_TOKEN_ID}" \
                --dry-run=client -o yaml | kubectl apply -f -
              kubectl -n "${NS}" create configmap vault-init-script \
                --from-file=vault-init.sh="${WORKSPACE}/.vault-init-from-jenkins.sh" \
                --dry-run=client -o yaml | kubectl apply -f -

              kubectl apply -f "${K8S_DIR}/mongodb.yaml" -f "${K8S_DIR}/vault.yaml"
              kubectl -n "${NS}" rollout status deployment/mongodb --timeout=180s
              kubectl -n "${NS}" rollout status deployment/vault --timeout=180s

              kubectl -n "${NS}" delete job vault-init --ignore-not-found
              kubectl apply -f "${K8S_DIR}/job-vault-init.yaml"
              kubectl -n "${NS}" wait --for=condition=complete job/vault-init --timeout=300s

              kubectl apply -f "${K8S_DIR}/deployment-app.yaml" -f "${K8S_DIR}/service-app.yaml"
              kubectl -n "${NS}" rollout restart deployment/task-manager-bot
              kubectl -n "${NS}" rollout status deployment/task-manager-bot --timeout=400s
            '''
          }
        }
      }
    }

    stage('Check Status') {
      when {
        expression { return !params.SKIP_K8S_DEPLOY }
      }
      steps {
        sh '''
          set -euo pipefail
          export PATH="/usr/local/bin:/usr/bin:/bin:${PATH}"
          export KUBECONFIG="${WORKSPACE}/.kubeconfig-run"
          echo "📜 Pods:"
          kubectl get pods -n "${NAMESPACE}" -o wide
          echo "🌐 Services:"
          kubectl get svc -n "${NAMESPACE}"
        '''
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}