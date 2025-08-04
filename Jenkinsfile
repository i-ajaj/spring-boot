paramters

pipeline {
    agent any
    params.
    environment {
        COMPOSE_PROJECT_NAME = 'myapp'
    }

    stages {
        stage('Deployment') {
            steps {
                sh 'docker ps'
            }
        }
    }
    post {
        always {
            echo "Cleaning up containers..."
        }
    }
}
