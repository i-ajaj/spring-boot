pipeline {
  agent any

  environment {
    // --- repos ---
    SPRING_REPO = "https://github.com/i-ajaj/spring-boot.git"
    WORKER_REPO = "https://github.com/i-ajaj/python-worker.git"

    // --- docker images (local names ok with kind load) ---
    SPRING_IMAGE = "spring-boot-app"
    WORKER_IMAGE = "python-worker-app"

    // --- k8s/helm ---
    KIND_CLUSTER = "kind-tasks"
    K8S_NAMESPACE = "tasks-app"
    // If Jenkins runs as another user, point this to that user’s kubeconfig:
    KUBECONFIG = "/home/azureuser/.kube/config"
  }

  stages {
    stage('Cloning Repos') {
      steps {
        script {
          def isManual = currentBuild.getBuildCauses()
            .any { cause -> cause.toString().contains("UserIdCause") }
          if (isManual) {
            echo "Manual build detected — cloning both repos"
          } else {
            echo "Webhook triggered by repo: ${env.REPO_NAME}"
          }

          if (env.REPO_NAME == 'spring-boot') {
            dir('spring-boot') { git url: "${SPRING_REPO}", branch: 'main' }
            dir('python-worker') { git url: "${WORKER_REPO}", branch: 'main' }
          } else if (env.REPO_NAME == 'python-worker') {
            dir('python-worker') { git url: "${WORKER_REPO}", branch: 'main' }
            dir('spring-boot') { git url: "${SPRING_REPO}", branch: 'main' }
          } else {
            dir('spring-boot') { git url: "${SPRING_REPO}", branch: 'main' }
            dir('python-worker') { git url: "${WORKER_REPO}", branch: 'main' }
          }
        }
      }
    }

    stage('Build Docker Images') {
      steps {
        sh '''
          set -euo pipefail
          docker pull postgres
          docker pull rabbitmq:3-management
          docker build -t ${SPRING_IMAGE}:latest spring-boot
          docker build -t ${WORKER_IMAGE}:latest python-worker
          docker image ls | grep -E "${SPRING_IMAGE}|${WORKER_IMAGE}" || true
        '''
      }
    }

    stage('Tag Images with Version & Load into kind') {
      steps {
        script {
          // Use Jenkins BUILD_NUMBER as the version. You can switch to git sha if you prefer.
          env.APP_VERSION = "${env.BUILD_NUMBER}"
        }
        sh '''
          set -euo pipefail

          echo "Using version: ${APP_VERSION}"

          # Tag images
          docker tag ${SPRING_IMAGE}:latest ${SPRING_IMAGE}:${APP_VERSION}
          docker tag ${WORKER_IMAGE}:latest ${WORKER_IMAGE}:${APP_VERSION}

          # Load images into kind nodes so no registry is required
          kind load docker-image ${SPRING_IMAGE}:${APP_VERSION} --name ${KIND_CLUSTER}
          kind load docker-image ${WORKER_IMAGE}:${APP_VERSION} --name ${KIND_CLUSTER}

          docker image ls | grep -E "${SPRING_IMAGE}|${WORKER_IMAGE}" || true
        '''
      }
    }

    stage('Helm Setup & Deploy') {
      steps {
        sh '''
          set -euo pipefail

          # Check cluster reachability
          kubectl config current-context
          kubectl cluster-info

          # Upgrade/Install spring-app
          helm upgrade --install spring-app ./charts/spring-app \
            --namespace ${K8S_NAMESPACE} \
            --set image.repository=${SPRING_IMAGE} \
            --set image.tag=${APP_VERSION} \
            --set image.pullPolicy=IfNotPresent \
            --wait --timeout 5m

          # Upgrade/Install python-worker
          helm upgrade --install python-worker ./charts/python-worker \
            --namespace ${K8S_NAMESPACE} \
            --set image.repository=${WORKER_IMAGE} \
            --set image.tag=${APP_VERSION} \
            --set image.pullPolicy=IfNotPresent \
            --wait --timeout 5m

          echo "=== Helm Releases ==="
          helm list -n ${K8S_NAMESPACE}

          echo "=== Pods ==="
          kubectl get pods -n ${K8S_NAMESPACE} -o wide
        '''
      }
    }
  }

  post {
    failure {
      echo "❌ Failure — showing pod states"
      sh 'kubectl get pods -A || true'
    }
  }
}
