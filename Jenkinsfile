pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    skipDefaultCheckout(true)
  }

  environment {
    TF_DIR = 'infra/terraform'
    ANSIBLE_DIR = 'infra/ansible'
    APP_DIR = '.'
  }

  stages {
    stage('Checkout') {
      steps {
        deleteDir()
        checkout scm
      }
    }

    stage('Build fat-jar (JDK 23)') {
      steps {
        sh '''
          set -euo pipefail
          docker run --rm \
            -v "$PWD:/ws" -w "/ws/${APP_DIR}" \
            maven:3.9.9-eclipse-temurin-23 \
            mvn -B -DskipTests package
        '''
      }
      post {
        success {
          archiveArtifacts artifacts: "target/*.jar", fingerprint: true
        }
      }
    }

    stage('Terraform init/apply') {
      steps {
        withCredentials([
          string(credentialsId: 'YC_TOKEN', variable: 'YC_TOKEN'),
          string(credentialsId: 'YC_CLOUD_ID', variable: 'YC_CLOUD_ID'),
          string(credentialsId: 'YC_FOLDER_ID', variable: 'YC_FOLDER_ID'),
          string(credentialsId: 'YC_SSH_PUBLIC_KEY', variable: 'YC_SSH_PUBLIC_KEY')
        ]) {
          sh '''
            set -euo pipefail
            export YC_TOKEN YC_CLOUD_ID YC_FOLDER_ID

            cd "${TF_DIR}"
            terraform init -input=false
            terraform apply -auto-approve -input=false \
              -var "cloud_id=${YC_CLOUD_ID}" \
              -var "folder_id=${YC_FOLDER_ID}" \
              -var "ssh_public_key=${YC_SSH_PUBLIC_KEY}"
          '''
        }
      }
    }

    stage('Build & push image to YCR') {
      steps {
        withCredentials([
          string(credentialsId: 'YC_TOKEN', variable: 'YC_TOKEN')
        ]) {
          sh '''
            set -euo pipefail
            cd "${TF_DIR}"
            REGISTRY_ID="$(terraform output -raw registry_id)"
            cd -

            IMAGE_TAG="${GIT_COMMIT:-manual}"
            APP_IMAGE="cr.yandex/${REGISTRY_ID}/task-manager-bot:${IMAGE_TAG}"

            docker build -t "${APP_IMAGE}" .
            echo "${YC_TOKEN}" | docker login --username oauth --password-stdin cr.yandex
            docker push "${APP_IMAGE}"

            echo "${APP_IMAGE}" > .app_image
          '''
        }
      }
    }

    stage('Ansible provision & deploy') {
      steps {
        withCredentials([
          string(credentialsId: 'YC_TOKEN', variable: 'YC_TOKEN'),
          string(credentialsId: 'VAULT_DEV_ROOT_TOKEN_ID', variable: 'VAULT_DEV_ROOT_TOKEN_ID'),
          string(credentialsId: 'MONGO_INITDB_ROOT_USERNAME', variable: 'MONGO_INITDB_ROOT_USERNAME'),
          string(credentialsId: 'MONGO_INITDB_ROOT_PASSWORD', variable: 'MONGO_INITDB_ROOT_PASSWORD'),
          sshUserPrivateKey(credentialsId: 'YC_SSH_KEY', keyFileVariable: 'SSH_KEY_FILE', usernameVariable: 'SSH_USER')
        ]) {
          sh '''
            set -euo pipefail

            cd "${TF_DIR}"
            VM_IP="$(terraform output -raw vm_external_ip)"
            cd -

            APP_IMAGE="$(cat .app_image)"

            mkdir -p "${ANSIBLE_DIR}"
            cat > "${ANSIBLE_DIR}/inventory.ini" <<EOF
            [app]
            ${VM_IP} ansible_user=${SSH_USER} ansible_ssh_private_key_file=${SSH_KEY_FILE}
            EOF

            cd "${ANSIBLE_DIR}"
            ansible-playbook -i inventory.ini site.yml \
              -e "yc_token=${YC_TOKEN}" \
              -e "app_image=${APP_IMAGE}" \
              -e "vault_dev_root_token_id=${VAULT_DEV_ROOT_TOKEN_ID}" \
              -e "mongo_initdb_root_username=${MONGO_INITDB_ROOT_USERNAME}" \
              -e "mongo_initdb_root_password=${MONGO_INITDB_ROOT_PASSWORD}"
          '''
        }
      }
    }
  }

  // post actions removed: when checkout fails early, workspace context is missing
}

