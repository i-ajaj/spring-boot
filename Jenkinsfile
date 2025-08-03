pipeline {
    agent any

    environment {
        COMPOSE_PROJECT_NAME = 'myapp'
    }

    stages {
        stage('Deployment') {
            steps {
                sh ' docker-compose up -d'
            }
        }
    }
    post {
        always {
            echo "Cleaning up containers..."
        }
    }
}
