pipeline {
    agent any

    environment {
        COMPOSE_PROJECT_NAME = 'myapp'
    }

    stages {
        stage('Clone Repositories') {
            steps {
                // Clone repo 1 (backend)
                git url: 'https://github.com/your-org/repo-backend.git', branch: 'main', changelog: false, poll: false

                // Clone repo 2 (worker) into subdirectory
                dir('repo-worker') {
                    git url: 'https://github.com/your-org/repo-worker.git', branch: 'main', changelog: false, poll: false
                }
            }
        }

        stage('Docker Compose Up') {
            steps {
                script {
                    // Assuming docker-compose.yml is in root directory
                    sh 'docker-compose down || true'
                    sh 'docker-compose build'
                    sh 'docker-compose up -d'
                }
            }
        }
    }

    post {
        always {
            echo "Cleaning up containers..."
            sh 'docker-compose down'
        }
    }
}
