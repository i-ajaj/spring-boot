pipeline {
    agent any

    environment {
        COMPOSE_PROJECT_NAME = 'myapp'
    }

    stages {
        stage('Deployment') {
            steps {
                echo "Deploying"
            }
        }
    }
    post {
        always {
            echo "Cleaning up containers..."
        }
    }
}
