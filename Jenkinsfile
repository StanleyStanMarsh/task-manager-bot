// Репозиторий = корень этого проекта (task-manager-bot). В Jenkins: Pipeline from SCM, Script Path = Jenkinsfile.
// Если Git — монорепозиторий выше по дереву: Script Path = task-manager-bot/Jenkinsfile
//
// Credentials (Jenkins UI):
//   - openstack-noninteractive-rc : Secret file — exports (см. infra/openrc-noninteractive.example)
//   - task-manager-bot-env        : Secret file — содержимое .env для docker compose на ВМ
//
// Сборка: Java 23 (pom) — только в stage Build (JAVA_HOME), мастер Jenkins — JDK 21.
// OpenStack: при JUMP_HOST Heat выполняется на jump. Деплой на новую ВМ:
//   USE_PROXYJUMP_FOR_TARGET=true (по умолчанию) — тот же SSH_PRIVATE_KEY на агенте, цепочка ProxyJump (ключ на jump не нужен).
//   false — полный сценарий на jump; на jump должен лежать TARGET_SSH_KEY_ON_JUMP для входа на ВМ Heat.
// Логи: infra/scripts/jenkins-heat-and-deploy.sh
pipeline {
  agent any

  environment {
    MAVEN_JAVA_HOME = '/opt/java/temurin-23'
  }

  parameters {
    string(name: 'STACK_NAME', defaultValue: 'taskmgr-bot-stack', trim: true,
      description: 'Имя стека Heat (create или update).')
    string(name: 'SSH_PRIVATE_KEY', defaultValue: '/var/jenkins_home/.ssh/astafyev-key.pem', trim: true,
      description: 'Ключ на агенте Jenkins: к целевой ВМ (локальный режим) или к jump-хосту (если задан JUMP_HOST).')
    string(name: 'JUMP_HOST', defaultValue: '', trim: true,
      description: 'IP/hostname существующей ВМ (bastion), где доступен OpenStack API. Пусто = openstack с агента Jenkins.')
    string(name: 'JUMP_USER', defaultValue: 'ubuntu', trim: true,
      description: 'Пользователь SSH на jump (только при непустом JUMP_HOST).')
    booleanParam(name: 'USE_PROXYJUMP_FOR_TARGET', defaultValue: true,
      description: 'Если включено и задан JUMP_HOST — после Heat деплой с Jenkins через ProxyJump (ключ только на агенте). Если выключено — деплой с jump, нужен TARGET_SSH_KEY_ON_JUMP на ВМ.')
    string(name: 'TARGET_SSH_KEY_ON_JUMP', defaultValue: '/home/ubuntu/.ssh/astafyev-key.pem', trim: true,
      description: 'Только при USE_PROXYJUMP_FOR_TARGET=false: путь к ключу НА jump для SSH к новой ВМ.')
    string(name: 'HEAT_ENV_FILE', defaultValue: 'infra/heat-env.yaml', trim: true,
      description: 'Файл параметров Heat относительно корня checkout (этого репозитория).')
    string(name: 'SSH_USER', defaultValue: 'ubuntu', trim: true,
      description: 'Пользователь на новой ВМ (Heat).')
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
        withEnv([
          "JAVA_HOME=${env.MAVEN_JAVA_HOME}",
          "PATH+MAVENJAVA=${env.MAVEN_JAVA_HOME}/bin"
        ]) {
          sh 'mvn -B -ntp package'
        }
      }
    }

    stage('Heat + provision + deploy') {
      steps {
        withEnv([
          "STACK_NAME=${params.STACK_NAME}",
          "SSH_PRIVATE_KEY=${params.SSH_PRIVATE_KEY}",
          "JUMP_HOST=${params.JUMP_HOST}",
          "JUMP_USER=${params.JUMP_USER}",
          "USE_PROXYJUMP_FOR_TARGET=${params.USE_PROXYJUMP_FOR_TARGET ? '1' : '0'}",
          "TARGET_SSH_KEY_ON_JUMP=${params.TARGET_SSH_KEY_ON_JUMP}",
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
