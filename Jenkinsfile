// Репозиторий = корень этого проекта (task-manager-bot). В Jenkins: Pipeline from SCM, Script Path = Jenkinsfile.
// Если Git — монорепозиторий выше по дереву: Script Path = task-manager-bot/Jenkinsfile
//
// Credentials (Jenkins UI):
//   - openstack-noninteractive-rc : Secret file — exports (см. infra/openrc-noninteractive.example)
//   - task-manager-bot-env        : Secret file — содержимое .env для docker compose на ВМ
//
// Логи: Heat/SSH/docker выполняются в infra/scripts/jenkins-heat-and-deploy.sh с set -x;
// вывод stderr/stdout попадает в консоль job; при падении видна последняя выполненная команда.
pipeline {
  agent any

  parameters {
    string(name: 'STACK_NAME', defaultValue: 'taskmgr-bot-stack', trim: true,
      description: 'Имя стека Heat (create или update).')
    string(name: 'SSH_PRIVATE_KEY', defaultValue: '/var/jenkins_home/.ssh/astafyev-key.pem', trim: true,
      description: 'Путь к приватному ключу внутри агента Jenkins.')
    string(name: 'HEAT_ENV_FILE', defaultValue: 'infra/heat-env.yaml', trim: true,
      description: 'Файл параметров Heat относительно корня checkout (этого репозитория).')
    string(name: 'SSH_USER', defaultValue: 'ubuntu', trim: true)
    string(name: 'SSH_READY_TIMEOUT_SEC', defaultValue: '600', trim: true,
      description: 'Сколько секунд ждать SSH после CREATE_COMPLETE.')
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build JAR') {
      steps {
        sh 'mvn -B -ntp package'
      }
    }

    stage('Heat + provision + deploy') {
      steps {
        withEnv([
          "STACK_NAME=${params.STACK_NAME}",
          "SSH_PRIVATE_KEY=${params.SSH_PRIVATE_KEY}",
          "HEAT_ENV_FILE=${params.HEAT_ENV_FILE}",
          "SSH_USER=${params.SSH_USER}",
          "SSH_READY_TIMEOUT_SEC=${params.SSH_READY_TIMEOUT_SEC}"
        ]) {
          withCredentials([
            file(credentialsId: 'openstack-noninteractive-rc', variable: 'OPENSTACK_ENV'),
            file(credentialsId: 'task-manager-bot-env', variable: 'BOT_ENV_FILE')
          ]) {
            sh 'bash -x infra/scripts/jenkins-heat-and-deploy.sh'
          }
        }
      }
    }
  }
}
