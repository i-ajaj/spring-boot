pipeline {
    agent any
    environment {
        SPRING_REPO = "https://github.com/i-ajaj/spring-boot.git"
        WORKER_REPO = "https://github.com/i-ajaj/python-worker.git"
        SPRING_IMAGE = "spring-boot-app"
        WORKER_IMAGE = "python-worker-app"
        POSTGRES_IMAGE = "postgres"
        RABBITMQ_IMAGE = "rabbitmq:3-management"
        SPRING_NET = "spring-net"
        PGDATA_VOL = "pgdata"
        SHARED_VOL = "sred-data"
    }

    stages {
        stage('Cloning Repos') {
            steps {
                script {
                    def isManual = currentBuild.getBuildCauses()
                        .any { cause -> cause.toString().contains("UserIdCause") }

                    if (isManual) {
                        echo "Manual build detected"
                        echo "Skipped repos cloning"
                        // return
                    }

                    echo "Webhook triggered by repo: ${env.REPO_NAME}"
                    if (env.REPO_NAME == 'spring-boot') {
                        dir('spring-boot') {
                            git url: "${SPRING_REPO}", branch: 'main'
                        }
                    } else if (env.REPO_NAME == 'python-worker') {
                        dir('python-worker') {
                            git url: "${WORKER_REPO}", branch: 'main'
                        }
                    } else {
                        echo "Unknown repo, cloning both as fallback"
                        dir('spring-boot') {
                            git url: "${SPRING_REPO}", branch: 'main'
                        }
                        dir('python-worker') {
                            git url: "${WORKER_REPO}", branch: 'main'
                        }
                    }
                }
            }
        }

        stage('Building Docker Images') {
            steps {
                script {
                    sh 'docker pull postgres'
                    sh 'docker pull rabbitmq:3-management'
                    sh 'docker build -t ${SPRING_IMAGE}:latest spring-boot'
                    sh 'docker build -t ${WORKER_IMAGE}:latest python-worker'
                    sh 'docker image ls'
                }
            }
        }
    }

    post {
        success {
            sh "kp"
            echo "✅ All images are built."
            sh "docker image ls"
        } 
        failure {
            echo "❌ Build or run failure. Showing recent container logs..."
            sh '''
            docker logs spring-app || true
            docker logs rabbitmq || true
            docker logs postgres-container || true
            docker logs python-worker || true
            '''
        }
    }
}
