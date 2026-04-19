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
      description: 'Профиль minikube (как --name у kind). Драйвер на Mac: docker.'
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
            echo "Не найден контейнер узла minikube (профиль \${PROFILE}). На Mac: minikube status" >&2
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
          sh '''
            set -euo pipefail
            export PATH="/usr/local/bin:/usr/bin:/bin:${PATH}"
            command -v kubectl >/dev/null 2>&1 || { echo "Пересоберите образ Jenkins (cloud_study/Dockerfile)."; exit 1; }

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
            kubectl config view --raw > "${WORKSPACE}/.kubeconfig-run"
            export KUBECONFIG="${WORKSPACE}/.kubeconfig-run"
          '''

          sh """
            set -euo pipefail
            export PATH="/usr/local/bin:/usr/bin:/bin:\${PATH}"
            export KUBECONFIG="${WORKSPACE}/.kubeconfig-run"
            kubectl config use-context ${params.MINIKUBE_PROFILE}
            kubectl cluster-info
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

              kubectl apply -f "${K8S_DIR}/40-deployment-app.yaml" -f "${K8S_DIR}/50-service-app.yaml"
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
