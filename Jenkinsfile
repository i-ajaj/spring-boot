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
        SHARED_VOL = "shared-data"
    }

    stages {
        stage('Cloning Repos') {
            steps {
                script {
                    def isManual = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause) != null
                    if (isManual){
                        echo "Manual build detected"
                        echo "Skipped repos cloning"
                        return
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

        stage('Running Docker Containers') {
            steps {
                script {
                    // Create volumes and network if they don’t exist
                    sh '''
                    docker volume create --name ${PGDATA_VOL} || true
                    docker volume create --name ${SHARED_VOL} || true
                    docker network create ${SPRING_NET} || true
                    '''

                    // Start PostgreSQL
                    sh '''
                    docker rm -f postgres-container || true
                    docker run -d \
                        --name postgres-container \
                        --network ${SPRING_NET} \
                        -e POSTGRES_DB=tasks \
                        -e POSTGRES_USER=postgres \
                        -e POSTGRES_PASSWORD=password1 \
                        -e PGDATA=/var/lib/postgresql/data/pgdata \
                        -v ${PGDATA_VOL}:/var/lib/postgresql/data \
                        -p 5432:5432 \
                        ${POSTGRES_IMAGE}
                    '''

                    // Start RabbitMQ
                    sh '''
                    docker rm -f rabbitmq || true
                    docker run -d \
                        --name rabbitmq \
                        --network ${SPRING_NET} \
                        -p 5672:5672 -p 15672:15672 \
                        -v ${SHARED_VOL}:/app/shared \
                        -v ${SHARED_VOL}:/var/lib/rabbitmq \
                        --health-cmd='rabbitmq-diagnostics -q ping' \
                        --health-interval=10s \
                        --health-timeout=5s \
                        --health-retries=10 \
                        ${RABBITMQ_IMAGE}
                    '''

                    // Start Spring Boot App
                    sh '''
                    docker rm -f spring-app || true
                    docker run -d \
                        --name spring-app \
                        --network ${SPRING_NET} \
                        -v ${SHARED_VOL}:/app/shared \
                        -p 8081:8081 \
                        -e SPRING_PROFILES_ACTIVE=default \
                        -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-container:5432/tasks \
                        -e SPRING_DATASOURCE_USERNAME=postgres \
                        -e SPRING_DATASOURCE_PASSWORD=password1 \
                        -e SPRING_RABBITMQ_HOST=rabbitmq \
                        -e SPRING_RABBITMQ_PORT=5672 \
                        -e SPRING_RABBITMQ_USERNAME=guest \
                        -e SPRING_RABBITMQ_PASSWORD=guest \
                        ${SPRING_IMAGE}
                    '''

                    // Wait for RabbitMQ to be healthy
                    sh '''
                    until [ "$(docker inspect -f '{{.State.Health.Status}}' rabbitmq)" = "healthy" ]; do
                      echo "⏳ Waiting for RabbitMQ to be healthy..."
                      sleep 2
                    done

                    echo "✅ RabbitMQ is healthy!"
                    '''

                    // Start Python Worker
                    sh '''
                    docker rm -f python-worker || true
                    docker run -d \
                        --name python-worker \
                        --network ${SPRING_NET} \
                        -e SPRING_RABBITMQ_HOST=rabbitmq \
                        -e SPRING_RABBITMQ_PORT=5672 \
                        -e SPRING_RABBITMQ_USERNAME=guest \
                        -e SPRING_RABBITMQ_PASSWORD=guest \
                        -v ${SHARED_VOL}:/app/shared \
                        ${WORKER_IMAGE}
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "✅ All containers are up and running."
            sh "docker ps"
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
