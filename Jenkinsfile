pipeline {
  agent any

  environment {
    MAVEN_JAVA_HOME = '/opt/java/temurin-23'
  }

  parameters {
    string(name: 'STACK_NAME', defaultValue: 'lozhkina-taskmgr-bot-stack', trim: true,
      description: 'Имя стека Heat.')
    string(name: 'SSH_PRIVATE_KEY', defaultValue: '/var/jenkins_home/.ssh/lozhkina.pem', trim: true,
      description: 'SSH ключ.')
    string(name: 'JUMP_HOST', defaultValue: '', trim: true,
      description: 'IP/hostname существующей ВМ (bastion), где доступен OpenStack API.')
    string(name: 'JUMP_USER', defaultValue: 'ubuntu', trim: true,
      description: 'Пользователь SSH на jump.')
    booleanParam(name: 'USE_PROXYJUMP_FOR_TARGET', defaultValue: true,
      description: 'ProxyJump через Jenkins агента.')
    string(name: 'TARGET_SSH_KEY_ON_JUMP', defaultValue: '/home/ubuntu/.ssh/lozhkina.pem', trim: true,
      description: 'Путь к ключу НА jump.')
    string(name: 'HEAT_ENV_FILE', defaultValue: 'infra/heat-env.yaml', trim: true,
      description: 'Файл параметров Heat относительно корня checkout (этого репозитория).')
    string(name: 'SSH_USER', defaultValue: 'ubuntu', trim: true,
      description: 'Пользователь на новой ВМ (Heat).')
    string(name: 'SSH_READY_TIMEOUT_SEC', defaultValue: '600', trim: true,
      description: 'Таймаут ожидания SSH.')
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
