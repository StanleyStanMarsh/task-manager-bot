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
      description: 'Только сборка JAR и Docker-образ без kubectl.'
    )
    booleanParam(
      name: 'USE_LOCAL_K8S_CLUSTER',
      defaultValue: true,
      description: 'Локальный образ без registry + imagePullPolicy Never. Вместе с MINIKUBE_IMAGE_LOAD — выкат в minikube.'
    )
    booleanParam(
      name: 'MINIKUBE_IMAGE_LOAD',
      defaultValue: false,
      description: 'Включите для minikube: после docker build выполнится minikube image load (driver docker на Mac, том ~/.minikube в compose). Выключено — тот же сценарий, что и Kubernetes в Docker Desktop (общий Docker, без load).'
    )
    string(
      name: 'MINIKUBE_PROFILE',
      defaultValue: 'minikube',
      description: 'Профиль minikube (имя контекста kubectl по умолчанию совпадает с профилем).'
    )
    string(
      name: 'KUBERNETES_CONTEXT',
      defaultValue: '',
      description: 'Контекст kubectl (пусто: если включён MINIKUBE_IMAGE_LOAD — берётся MINIKUBE_PROFILE, иначе текущий из kubeconfig).'
    )
    string(
      name: 'IMAGE_REGISTRY',
      defaultValue: '',
      description: 'Если USE_LOCAL_K8S_CLUSTER выключен: реестр без завершающего /, образ <registry>/task-manager-bot:<BUILD_NUMBER>'
    )
  }

  environment {
    APP_DIR = '.'
    JENKINS_CONTAINER = 'jenkins-lab'
    K8S_DIR = 'k8s'
    K8S_NAMESPACE = 'task-manager-bot'
    IMAGE_NAME = 'task-manager-bot'
  }

  stages {
    stage('Checkout') {
      steps {
        cleanWs(
          deleteDirs: true,
          disableDeferredWipeout: true
        )
        checkout scm
      }
    }

    stage('Build fat-jar (JDK 23)') {
      steps {
        sh '''
          set -euo pipefail
          docker run --rm \
            --volumes-from "${JENKINS_CONTAINER}" \
            -w "${WORKSPACE}/${APP_DIR}" \
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

    stage('Docker build') {
      steps {
        sh '''
          set -euo pipefail
          docker build --platform linux/arm64 -t "${IMAGE_NAME}:${BUILD_NUMBER}" .
        '''
      }
    }

    stage('Minikube image load') {
      when {
        allOf {
          expression { return !params.SKIP_K8S_DEPLOY }
          expression { return params.USE_LOCAL_K8S_CLUSTER }
          expression { return params.MINIKUBE_IMAGE_LOAD }
        }
      }
      steps {
        withEnv(["MINIKUBE_PROFILE=${params.MINIKUBE_PROFILE}"]) {
          sh '''
            set -euo pipefail
            export MINIKUBE_HOME=/var/jenkins_home/.minikube-host
            if [ ! -d "${MINIKUBE_HOME}/profiles/${MINIKUBE_PROFILE}" ]; then
              echo "Нет ${MINIKUBE_HOME}/profiles/${MINIKUBE_PROFILE}. На Mac выполните: minikube start --driver=docker" >&2
              echo "И смонтируйте ~/.minikube в jenkins (см. cloud_study/docker-compose.yml)." >&2
              exit 1
            fi
            minikube version
            minikube image load "${IMAGE_NAME}:${BUILD_NUMBER}" --profile="${MINIKUBE_PROFILE}"
          '''
        }
      }
    }

    stage('Push image to registry') {
      when {
        allOf {
          expression { return !params.SKIP_K8S_DEPLOY }
          expression { return !params.USE_LOCAL_K8S_CLUSTER }
          expression { return params.IMAGE_REGISTRY?.trim() }
        }
      }
      steps {
        sh '''
          set -euo pipefail
          REG="${IMAGE_REGISTRY%/}"
          docker tag "${IMAGE_NAME}:${BUILD_NUMBER}" "${REG}/${IMAGE_NAME}:${BUILD_NUMBER}"
          docker push "${REG}/${IMAGE_NAME}:${BUILD_NUMBER}"
        '''
      }
    }

    stage('Deploy to Kubernetes') {
      when {
        expression { return !params.SKIP_K8S_DEPLOY }
      }
      steps {
        script {
          def kubeCtx = params.KUBERNETES_CONTEXT?.trim()
          if (!kubeCtx && params.MINIKUBE_IMAGE_LOAD) {
            kubeCtx = params.MINIKUBE_PROFILE ?: 'minikube'
          }
          withEnv([
            "USE_LOCAL_K8S_CLUSTER=${params.USE_LOCAL_K8S_CLUSTER}",
            "IMAGE_REGISTRY_PARAM=${params.IMAGE_REGISTRY ?: ''}",
            "KUBERNETES_CONTEXT_PARAM=${kubeCtx ?: ''}"
          ]) {
            withCredentials([
              file(credentialsId: 'VAULT_INIT_SH', variable: 'CRED_VAULT_INIT_SH'),
              string(credentialsId: 'VAULT_DEV_ROOT_TOKEN_ID', variable: 'VAULT_DEV_ROOT_TOKEN_ID'),
              string(credentialsId: 'MONGO_INITDB_ROOT_USERNAME', variable: 'MONGO_INITDB_ROOT_USERNAME'),
              string(credentialsId: 'MONGO_INITDB_ROOT_PASSWORD', variable: 'MONGO_INITDB_ROOT_PASSWORD')
            ]) {
              sh '''
                set -euo pipefail

                if [ -n "${KUBECONFIG:-}" ] && [ -f "${KUBECONFIG}" ]; then
                  echo "Using KUBECONFIG=${KUBECONFIG}"
                elif [ -f /var/jenkins_home/.kube-host/config ]; then
                  export KUBECONFIG=/var/jenkins_home/.kube-host/config
                  echo "Using mounted ~/.kube from host: ${KUBECONFIG}"
                elif [ -f /var/jenkins_home/.kube/config ]; then
                  export KUBECONFIG=/var/jenkins_home/.kube/config
                  echo "Using ${KUBECONFIG}"
                else
                  echo "Не найден kubeconfig. Смонтируйте ~/.kube в docker-compose или задайте KUBECONFIG." >&2
                  exit 1
                fi

                WORK_KUBECONFIG="${WORKSPACE}/.kubeconfig-jenkins-${BUILD_NUMBER}"
                kubectl config view --raw > "${WORK_KUBECONFIG}"
                export KUBECONFIG="${WORK_KUBECONFIG}"
                if [ -n "${KUBERNETES_CONTEXT_PARAM}" ]; then
                  kubectl config use-context "${KUBERNETES_CONTEXT_PARAM}"
                fi

                kubectl cluster-info

                if [ "${USE_LOCAL_K8S_CLUSTER}" = "true" ]; then
                  FULL_IMAGE="${IMAGE_NAME}:${BUILD_NUMBER}"
                  PULL_POLICY="Never"
                else
                  REG="${IMAGE_REGISTRY_PARAM}"
                  REG="${REG%/}"
                  if [ -z "${REG}" ]; then
                    echo "Для деплоя без USE_LOCAL_K8S_CLUSTER задайте IMAGE_REGISTRY." >&2
                    exit 1
                  fi
                  FULL_IMAGE="${REG}/${IMAGE_NAME}:${BUILD_NUMBER}"
                  PULL_POLICY="Always"
                fi

                NS="${K8S_NAMESPACE}"
                cp "${CRED_VAULT_INIT_SH}" "${WORKSPACE}/.vault-init-from-jenkins.sh"
                chmod 0755 "${WORKSPACE}/.vault-init-from-jenkins.sh"

                kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
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

                kubectl apply -f "${K8S_DIR}/10-mongodb.yaml" -f "${K8S_DIR}/20-vault.yaml"
                kubectl -n "${NS}" rollout status deployment/mongodb --timeout=180s
                kubectl -n "${NS}" rollout status deployment/vault --timeout=180s

                kubectl -n "${NS}" delete job vault-init --ignore-not-found
                kubectl apply -f "${K8S_DIR}/30-job-vault-init.yaml"
                kubectl -n "${NS}" wait --for=condition=complete job/vault-init --timeout=300s

                sed \
                  -e "s|IMAGE_PLACEHOLDER|${FULL_IMAGE}|g" \
                  -e "s|imagePullPolicy: Always|imagePullPolicy: ${PULL_POLICY}|g" \
                  "${K8S_DIR}/40-deployment-app.yaml" | kubectl apply -f -
                kubectl apply -f "${K8S_DIR}/50-service-app.yaml"
                kubectl -n "${NS}" rollout status deployment/task-manager-bot --timeout=400s
              '''
            }
          }
        }
      }
    }
  }
}
