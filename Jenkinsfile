/**
 * Один запуск без параметров: правьте значения в environment {} под свой Jenkins/облако.
 * Секреты .env: Jenkins credential «Secret file», ID = ENV_CREDENTIAL_ID.
 * IP ВМ для деплоя берётся из вывода Heat после этапа «Инфраструктура».
 */
pipeline {
    agent none

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        BUILD_AGENT_LABEL = 'built-in'
        HEAT_AGENT_LABEL = 'openstack-cli'
        DEPLOY_AGENT_LABEL = 'built-in'

        HEAT_STACK_NAME = 'task-manager-bot-stack'
        HEAT_TEMPLATE_PATH = 'infra/template.yaml'
        HEAT_PARAMETER_IMAGE = 'ununtu-22.04'
        HEAT_PARAMETER_FLAVOR = 'm1.small'
        HEAT_PARAMETER_KEY = 'astafye-key'
        HEAT_PARAMETER_SUBNET = '87af7ae7-714d-4472-b19a-7a4ec8505165'

        DEPLOY_USER = 'ubuntu'
        DEPLOY_REMOTE_DIR = '/opt/task-manager-bot'
        DEPLOY_RESTART_CMD = 'sudo docker compose -f /opt/task-manager-bot/docker-compose.yml restart app || true'
        ENV_CREDENTIAL_ID = 'task-manager-bot-env'

        MAVEN_DOCKER_IMAGE = 'maven:3.9.9-eclipse-temurin-23'
        // Пусто: docker run с -v $WORKSPACE. Или имя контейнера для --volumes-from.
        JENKINS_CONTAINER = ''
    }

    stages {
        stage('Сборка') {
            agent { label "${env.BUILD_AGENT_LABEL}" }
            steps {
                checkout scm
                script {
                    def ws = env.WORKSPACE
                    def volFrom = env.JENKINS_CONTAINER?.trim()
                    def image = env.MAVEN_DOCKER_IMAGE
                    if (volFrom) {
                        sh """
                            set -euo pipefail
                            docker run --rm \\
                              --volumes-from "${volFrom}" \\
                              -w "${ws}" \\
                              ${image} \\
                              mvn -B -ntp clean package -DskipTests
                        """
                    } else {
                        sh """
                            set -euo pipefail
                            docker run --rm \\
                              -v "${ws}:${ws}" \\
                              -w "${ws}" \\
                              ${image} \\
                              mvn -B -ntp clean package -DskipTests
                        """
                    }
                    archiveArtifacts artifacts: 'target/task-manager-bot-*.jar', fingerprint: true, onlyIfSuccessful: true
                    stash name: 'app-jar', includes: 'target/task-manager-bot-*.jar'
                    stash name: 'heat-infra', includes: 'infra/**'
                }
            }
        }

        stage('Инфраструктура') {
            agent { label "${env.HEAT_AGENT_LABEL}" }
            steps {
                script {
                    def stack = env.HEAT_STACK_NAME
                    def ip
                    dir('infra-work') {
                        deleteDir()
                        unstash 'heat-infra'
                        def tpl = env.HEAT_TEMPLATE_PATH
                        def commonArgs = "-t ${tpl} " +
                            "--parameter image_id=${env.HEAT_PARAMETER_IMAGE} " +
                            "--parameter flavor_id=${env.HEAT_PARAMETER_FLAVOR} " +
                            "--parameter key_name=${env.HEAT_PARAMETER_KEY} " +
                            "--parameter existing_subnet_id=${env.HEAT_PARAMETER_SUBNET} " +
                            "${stack}"
                        sh """
                            set -euo pipefail
                            if openstack stack show ${stack} >/dev/null 2>&1; then
                              openstack stack update ${commonArgs} --wait
                            else
                              openstack stack create ${commonArgs} --wait
                            fi
                            openstack stack show ${stack} -c stack_status -f value
                        """
                        ip = sh(
                            script: "openstack stack output show ${stack} server_private_ip -f value -c output_value",
                            returnStdout: true
                        ).trim()
                    }
                    writeFile file: 'deploy-host.txt', text: ip
                    stash name: 'deploy-host', includes: 'deploy-host.txt'
                }
            }
        }

        stage('Деплой') {
            agent { label "${env.DEPLOY_AGENT_LABEL}" }
            steps {
                script {
                    unstash 'app-jar'
                    unstash 'heat-infra'
                    unstash 'deploy-host'
                    def host = readFile('deploy-host.txt').trim()
                    if (!host) {
                        error('Пустой IP из Heat (server_private_ip)')
                    }
                    def jarFile = sh(script: 'ls target/task-manager-bot-*.jar | head -1', returnStdout: true).trim()

                    withCredentials([file(credentialsId: env.ENV_CREDENTIAL_ID, variable: 'BOT_ENV_FILE')]) {
                        sshagent(['ssh-deploy-key']) {
                            sh """
                                set -e
                                scp -o StrictHostKeyChecking=no \$BOT_ENV_FILE ${env.DEPLOY_USER}@${host}:${env.DEPLOY_REMOTE_DIR}/.env
                                scp -o StrictHostKeyChecking=no ${jarFile} ${env.DEPLOY_USER}@${host}:${env.DEPLOY_REMOTE_DIR}/task-manager-bot.jar
                                ssh -o StrictHostKeyChecking=no ${env.DEPLOY_USER}@${host} '${env.DEPLOY_RESTART_CMD}'
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        failure {
            echo 'Проверьте Docker, OpenStack, SSH credential ssh-deploy-key, Secret file ENV_CREDENTIAL_ID.'
        }
    }
}
