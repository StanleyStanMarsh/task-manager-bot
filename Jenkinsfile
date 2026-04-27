/**
 * Сборка Maven в Docker как в рабочем пайплайне: cleanWs + один checkout, --volumes-from контейнера Jenkins.
 * Путь к pom.xml ищется через find (не fileExists — избегаем «грязного» workspace).
 * JENKINS_CONTAINER: имя контейнера Jenkins (у вас jenkins-lab). Пусто — fallback: -v \$WORKSPACE:\$WORKSPACE
 */
pipeline {
    agent none

    options {
        timestamps()
        skipDefaultCheckout(true)
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
        // Имя контейнера Jenkins — как в рабочем пайплайне (docker inspect …). Пусто = только bind-mount.
        JENKINS_CONTAINER = 'jenkins-lab'

        // Не пусто — принудительно этот подкаталог (относительно workspace), иначе ищем pom.xml через find
        PROJECT_SUBDIR = ''
    }

    stages {
        stage('Сборка') {
            agent { label "${env.BUILD_AGENT_LABEL}" }
            steps {
                script {
                    cleanWs(deleteDirs: true, disableDeferredWipeout: true)
                    checkout scm

                    def manual = env.PROJECT_SUBDIR?.trim()
                    if (manual) {
                        writeFile file: 'project-subdir.txt', text: manual
                        if (!fileExists("${manual}/pom.xml")) {
                            error("Нет ${manual}/pom.xml — проверьте PROJECT_SUBDIR.")
                        }
                    } else {
                        sh '''
                            set -euo pipefail
                            cd "${WORKSPACE}"
                            POM=$(find . -maxdepth 10 -type f -name pom.xml ! -path '*/target/*' ! -path '*/.git/*' | head -1)
                            if [ -z "${POM}" ]; then
                              echo "pom.xml не найден. Содержимое workspace:" >&2
                              find . -maxdepth 3 -type f -o -type d | head -80
                              exit 1
                            fi
                            REL="$(dirname "${POM}")"
                            REL="${REL#./}"
                            printf '%s' "${REL}" > "${WORKSPACE}/project-subdir.txt"
                            echo "Используется каталог Maven: ${WORKSPACE}/${REL:-.}"
                        '''
                    }

                    def sub = readFile('project-subdir.txt').trim()
                    def workDir = sub ? "${env.WORKSPACE}/${sub}" : env.WORKSPACE
                    def volFrom = env.JENKINS_CONTAINER?.trim()
                    def image = env.MAVEN_DOCKER_IMAGE

                    if (volFrom) {
                        sh """
                            set -euo pipefail
                            docker run --rm \\
                              --volumes-from "${volFrom}" \\
                              -w "${workDir}" \\
                              ${image} \\
                              mvn -B -ntp clean package -DskipTests
                        """
                    } else {
                        sh """
                            set -euo pipefail
                            docker run --rm \\
                              -v "${env.WORKSPACE}:${env.WORKSPACE}" \\
                              -w "${workDir}" \\
                              ${image} \\
                              mvn -B -ntp clean package -DskipTests
                        """
                    }

                    stash name: 'project-meta', includes: 'project-subdir.txt'
                    def jarGlob = sub ? "${sub}/target/task-manager-bot-*.jar" : 'target/task-manager-bot-*.jar'
                    def infraGlob = sub ? "${sub}/infra/**" : 'infra/**'
                    archiveArtifacts artifacts: jarGlob, fingerprint: true, onlyIfSuccessful: true
                    stash name: 'app-jar', includes: jarGlob
                    stash name: 'heat-infra', includes: infraGlob
                }
            }
        }

        stage('Инфраструктура') {
            agent { label "${env.HEAT_AGENT_LABEL}" }
            steps {
                script {
                    unstash 'project-meta'
                    def sub = readFile('project-subdir.txt').trim()

                    def stack = env.HEAT_STACK_NAME
                    def ip
                    dir('infra-work') {
                        deleteDir()
                        unstash 'heat-infra'
                        def tpl = sub ? "${sub}/${env.HEAT_TEMPLATE_PATH}" : env.HEAT_TEMPLATE_PATH
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
                    unstash 'project-meta'
                    def sub = readFile('project-subdir.txt').trim()

                    unstash 'app-jar'
                    unstash 'heat-infra'
                    unstash 'deploy-host'
                    def host = readFile('deploy-host.txt').trim()
                    if (!host) {
                        error('Пустой IP из Heat (server_private_ip)')
                    }
                    def jarGlob = sub ? "${sub}/target/task-manager-bot-*.jar" : 'target/task-manager-bot-*.jar'
                    def jarFile = sh(script: "ls ${jarGlob} | head -1", returnStdout: true).trim()

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
            echo 'cleanWs+checkout, JENKINS_CONTAINER=jenkins-lab, find pom.xml, OpenStack, ssh-deploy-key, ENV credential.'
        }
    }
}
