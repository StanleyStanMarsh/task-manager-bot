pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    skipDefaultCheckout(true)
    disableConcurrentBuilds()
  }

  environment {
    TF_DIR = 'infra/terraform'
    ANSIBLE_DIR = 'infra/ansible'
    APP_DIR = '.'
    JENKINS_CONTAINER = 'jenkins-lab'
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
          string(credentialsId: 'YC_SSH_PUBLIC_KEY', variable: 'YC_SSH_PUBLIC_KEY'),
          string(credentialsId: 'YC_SUBNET_ID', variable: 'YC_SUBNET_ID'),
          string(credentialsId: 'YC_SECURITY_GROUP_ID', variable: 'YC_SECURITY_GROUP_ID')
        ]) {
          sh '''
            set -euo pipefail
            export YC_TOKEN YC_CLOUD_ID YC_FOLDER_ID

            cd "${TF_DIR}"
            terraform init -input=false
            terraform apply -auto-approve -input=false \
              -var "cloud_id=${YC_CLOUD_ID}" \
              -var "folder_id=${YC_FOLDER_ID}" \
              -var "ssh_public_key=${YC_SSH_PUBLIC_KEY}" \
              -var "subnet_id=${YC_SUBNET_ID}" \
              -var "security_group_id=${YC_SECURITY_GROUP_ID}"
          '''
        }
      }
    }

    stage('Ansible provision & deploy') {
      steps {
        withCredentials([
          string(credentialsId: 'VAULT_DEV_ROOT_TOKEN_ID', variable: 'VAULT_DEV_ROOT_TOKEN_ID'),
          string(credentialsId: 'MONGO_INITDB_ROOT_USERNAME', variable: 'MONGO_INITDB_ROOT_USERNAME'),
          string(credentialsId: 'MONGO_INITDB_ROOT_PASSWORD', variable: 'MONGO_INITDB_ROOT_PASSWORD'),
          file(credentialsId: 'VAULT_INIT_SH', variable: 'CRED_VAULT_INIT_SH'),
          sshUserPrivateKey(credentialsId: 'YC_SSH_KEY', keyFileVariable: 'SSH_KEY_FILE', usernameVariable: 'SSH_USER')
        ]) {
          sh '''
            set -euo pipefail

            mkdir -p "${ANSIBLE_DIR}/files"
            cp "${CRED_VAULT_INIT_SH}" "${ANSIBLE_DIR}/files/vault-init.sh"
            chmod 0755 "${ANSIBLE_DIR}/files/vault-init.sh"
            printf '%s\n' "${VAULT_DEV_ROOT_TOKEN_ID}" > "${ANSIBLE_DIR}/files/vault_root_token.txt"
            chmod 0600 "${ANSIBLE_DIR}/files/vault_root_token.txt"

            cd "${TF_DIR}"
            VM_IP="$(terraform output -raw vm_external_ip)"
            cd -

            mkdir -p "${ANSIBLE_DIR}"
            {
              echo '[app]'
              echo "${VM_IP} ansible_user=${SSH_USER} ansible_ssh_private_key_file=${SSH_KEY_FILE}"
            } > "${ANSIBLE_DIR}/inventory.ini"

            echo "Waiting for sshd on ${VM_IP} (fresh VM often needs 30–120s)..."
            SSH_PROBE_OPTS="-o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
            READY=0
            for i in $(seq 1 36); do
              if ssh ${SSH_PROBE_OPTS} -i "${SSH_KEY_FILE}" "${SSH_USER}@${VM_IP}" "exit 0" 2>/dev/null; then
                READY=1
                echo "SSH is up (attempt ${i})."
                break
              fi
              echo "SSH not ready yet (attempt ${i}/36), sleeping 10s..."
              sleep 10
            done
            if [ "${READY}" != 1 ]; then
              echo "SSH never became ready — check security group (ingress TCP 22) and cloud-init on the VM."
              exit 1
            fi

            cd "${ANSIBLE_DIR}"
            ansible-playbook -i inventory.ini site.yml \
              -e "vault_dev_root_token_id=${VAULT_DEV_ROOT_TOKEN_ID}" \
              -e "mongo_initdb_root_username=${MONGO_INITDB_ROOT_USERNAME}" \
              -e "mongo_initdb_root_password=${MONGO_INITDB_ROOT_PASSWORD}"
          '''
        }
      }
    }
  }

  post {
    always {
      script {
        withCredentials([
          string(credentialsId: 'YC_TOKEN', variable: 'YC_TOKEN'),
          string(credentialsId: 'YC_CLOUD_ID', variable: 'YC_CLOUD_ID'),
          string(credentialsId: 'YC_FOLDER_ID', variable: 'YC_FOLDER_ID'),
          string(credentialsId: 'YC_SSH_PUBLIC_KEY', variable: 'YC_SSH_PUBLIC_KEY'),
          string(credentialsId: 'YC_SUBNET_ID', variable: 'YC_SUBNET_ID'),
          string(credentialsId: 'YC_SECURITY_GROUP_ID', variable: 'YC_SECURITY_GROUP_ID')
        ]) {
          sh '''
            set +e
            echo "=== Terraform destroy (очистка инфраструктуры, всегда в конце) ==="
            if [ ! -d "${TF_DIR}" ]; then
              echo "Каталог ${TF_DIR} отсутствует — пропуск destroy"
              exit 0
            fi
            cd "${TF_DIR}"
            if [ ! -f terraform.tfstate ] && [ ! -f .terraform/terraform.tfstate ]; then
              echo "Нет terraform state — пропуск destroy"
              exit 0
            fi
            export YC_TOKEN YC_CLOUD_ID YC_FOLDER_ID
            terraform init -input=false
            terraform destroy -auto-approve -input=false \
              -var "cloud_id=${YC_CLOUD_ID}" \
              -var "folder_id=${YC_FOLDER_ID}" \
              -var "ssh_public_key=${YC_SSH_PUBLIC_KEY}" \
              -var "subnet_id=${YC_SUBNET_ID}" \
              -var "security_group_id=${YC_SECURITY_GROUP_ID}" || true
            echo "Terraform destroy завершён (код выше мог быть ненулевым)"
            exit 0
          '''
        }
      }
    }
  }
}
