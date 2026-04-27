pipeline {
    agent none

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'BUILD_AGENT_LABEL', defaultValue: 'built-in', description: 'Агент для Maven')
        string(name: 'HEAT_AGENT_LABEL', defaultValue: 'openstack-cli', description: 'Агент с openstack CLI')
        string(name: 'DEPLOY_AGENT_LABEL', defaultValue: 'built-in', description: 'Агент для scp/ssh к ВМ')

        choice(name: 'HEAT_ACTION', choices: ['create', 'update'], description: 'create — новый стек, update — обновление')
        string(name: 'HEAT_STACK_NAME', defaultValue: 'task-manager-bot-stack', description: 'Имя стека')
        string(name: 'HEAT_TEMPLATE_PATH', defaultValue: 'infra/template.yaml', description: 'Шаблон Heat от корня репо')
        string(name: 'HEAT_PARAMETER_IMAGE', defaultValue: 'ununtu-22.04', description: 'image_id')
        string(name: 'HEAT_PARAMETER_FLAVOR', defaultValue: 'm1.small', description: 'flavor_id')
        string(name: 'HEAT_PARAMETER_KEY', defaultValue: 'astafye-key', description: 'key_name')
        string(name: 'HEAT_PARAMETER_SUBNET', defaultValue: '87af7ae7-714d-4472-b19a-7a4ec8505165', description: 'existing_subnet_id')

        string(name: 'DEPLOY_HOST', defaultValue: '', description: 'IP ВМ (stack output server_private_ip)')
        string(name: 'DEPLOY_USER', defaultValue: 'ubuntu', description: 'SSH user')
        string(name: 'DEPLOY_REMOTE_DIR', defaultValue: '/opt/task-manager-bot', description: 'Каталог на сервере')
        text(
            name: 'DEPLOY_RESTART_CMD',
            defaultValue: 'sudo docker compose -f /opt/task-manager-bot/docker-compose.yml restart app || true',
            description: 'Команда на ВМ после копирования JAR'
        )
        booleanParam(
            name: 'RUN_ENVIRONMENT_SETUP',
            defaultValue: false,
            description: 'Выполнить infra/environment.sh на ВМ под sudo (Docker/Java/том Mongo и т.д.). Обычно один раз после первого Heat; повторный запуск долгий и не всегда нужен.'
        )
    }

    stages {
        stage('Сборка') {
            agent { label "${params.BUILD_AGENT_LABEL}" }
            steps {
                checkout scm
                sh 'mvn -B -ntp clean package -DskipTests'
                archiveArtifacts artifacts: 'target/task-manager-bot-*.jar', fingerprint: true, onlyIfSuccessful: true
                stash name: 'app-jar', includes: 'target/task-manager-bot-*.jar'
                stash name: 'heat-infra', includes: 'infra/**'
            }
        }

        stage('Инфраструктура') {
            agent { label "${params.HEAT_AGENT_LABEL}" }
            steps {
                dir('infra-work') {
                    deleteDir()
                    unstash 'heat-infra'
                    script {
                        def tpl = params.HEAT_TEMPLATE_PATH
                        def commonArgs = "-t ${tpl} " +
                            "--parameter image_id=${params.HEAT_PARAMETER_IMAGE} " +
                            "--parameter flavor_id=${params.HEAT_PARAMETER_FLAVOR} " +
                            "--parameter key_name=${params.HEAT_PARAMETER_KEY} " +
                            "--parameter existing_subnet_id=${params.HEAT_PARAMETER_SUBNET} " +
                            "${params.HEAT_STACK_NAME}"
                        def cmd = params.HEAT_ACTION == 'create'
                            ? "openstack stack create ${commonArgs} --wait"
                            : "openstack stack update ${commonArgs} --wait"
                        // withCredentials([file(credentialsId: 'openstack-clouds', variable: 'OS_CLIENT_CONFIG_FILE')]) { sh cmd }
                        sh cmd
                        sh "openstack stack show ${params.HEAT_STACK_NAME} -c stack_status -f value"
                    }
                }
            }
        }

        stage('Деплой') {
            agent { label "${params.DEPLOY_AGENT_LABEL}" }
            steps {
                script {
                    def host = params.DEPLOY_HOST?.trim()
                    if (!host) {
                        error('Задайте параметр DEPLOY_HOST')
                    }
                    unstash 'app-jar'
                    unstash 'heat-infra'
                    def jarFile = sh(script: 'ls target/task-manager-bot-*.jar | head -1', returnStdout: true).trim()
                    sshagent(['ssh-deploy-key']) {
                        sh """
                            set -e
                            if [ "${params.RUN_ENVIRONMENT_SETUP}" = "true" ]; then
                              scp -o StrictHostKeyChecking=no infra/environment.sh ${params.DEPLOY_USER}@${host}:/tmp/environment-setup.sh
                              ssh -o StrictHostKeyChecking=no ${params.DEPLOY_USER}@${host} 'sudo bash /tmp/environment-setup.sh'
                            fi
                            scp -o StrictHostKeyChecking=no ${jarFile} ${params.DEPLOY_USER}@${host}:${params.DEPLOY_REMOTE_DIR}/task-manager-bot.jar
                            ssh -o StrictHostKeyChecking=no ${params.DEPLOY_USER}@${host} '${params.DEPLOY_RESTART_CMD}'
                        """
                    }
                }
            }
        }
    }

    post {
        failure {
            echo 'Проверьте агенты, OpenStack, SSH credential ssh-deploy-key, DEPLOY_HOST.'
        }
    }
}
