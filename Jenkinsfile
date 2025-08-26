pipeline {
  agent any

  environment {
    SPRING_REPO = "https://github.com/i-ajaj/spring-boot.git"
    WORKER_REPO = "https://github.com/i-ajaj/python-worker.git"

    SPRING_IMAGE = "spring-boot-app"
    WORKER_IMAGE = "python-worker-app"

    KIND_CLUSTER = "kind-tasks"
    K8S_NAMESPACE = "tasks-app"
  }

  options { ansiColor('xterm'); timestamps() }

  stages {

    stage('Preflight') {
      steps {
        sh '''#!/usr/bin/env bash
set -euo pipefail
echo "== Tool versions =="
command -v docker || { echo "docker not found"; exit 1; }
command -v kind   || { echo "kind not found"; exit 1; }
command -v kubectl|| { echo "kubectl not found"; exit 1; }
command -v helm   || { echo "helm not found"; exit 1; }
docker version --format '{{.Server.Version}}' || true
kind get clusters || true
helm version || true
        '''
      }
    }

    stage('Cloning Repos') {
      steps {
        script {
          def isManual = currentBuild.getBuildCauses().any { it.toString().contains("UserIdCause") }
          echo isManual ? "Manual build detected — cloning both repos" : "Webhook triggered by repo: ${env.REPO_NAME}"

          if (env.REPO_NAME == 'spring-boot') {
            dir('spring-boot')    { git url: "${SPRING_REPO}", branch: 'main' }
            dir('python-worker')  { git url: "${WORKER_REPO}", branch: 'main' }
          } else if (env.REPO_NAME == 'python-worker') {
            dir('python-worker')  { git url: "${WORKER_REPO}", branch: 'main' }
            dir('spring-boot')    { git url: "${SPRING_REPO}", branch: 'main' }
          } else {
            dir('spring-boot')    { git url: "${SPRING_REPO}", branch: 'main' }
            dir('python-worker')  { git url: "${WORKER_REPO}", branch: 'main' }
          }
        }
      }
    }

    stage('Build Docker Images') {
      steps {
        sh '''#!/usr/bin/env bash
set -euo pipefail
docker pull postgres
docker pull rabbitmq:3-management
docker build -t ${SPRING_IMAGE}:latest spring-boot
docker build -t ${WORKER_IMAGE}:latest python-worker
docker image ls | grep -E "${SPRING_IMAGE}|${WORKER_IMAGE}" || true
        '''
      }
    }

    stage('Tag & Load into kind') {
      steps {
        script { env.APP_VERSION = "${env.BUILD_NUMBER}" } // or a git sha
        sh '''#!/usr/bin/env bash
set -euo pipefail
echo "Using version: ${APP_VERSION}"

docker tag ${SPRING_IMAGE}:latest ${SPRING_IMAGE}:${APP_VERSION}
docker tag ${WORKER_IMAGE}:latest ${WORKER_IMAGE}:${APP_VERSION}

# Load into kind nodes (no registry needed)
kind load docker-image ${SPRING_IMAGE}:${APP_VERSION} --name ${KIND_CLUSTER}
kind load docker-image ${WORKER_IMAGE}:${APP_VERSION} --name ${KIND_CLUSTER}

docker image ls | grep -E "${SPRING_IMAGE}|${WORKER_IMAGE}" || true
        '''
      }
    }

    stage('Helm Deploy (versioned)') {
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-tasks', variable: 'KUBECFG')]) {
          sh '''#!/usr/bin/env bash
set -euo pipefail
export KUBECONFIG="${KUBECFG}"

kubectl config current-context
kubectl cluster-info

# Ensure namespace exists
kubectl get ns ${K8S_NAMESPACE} >/dev/null 2>&1 || kubectl create ns ${K8S_NAMESPACE}

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
  }

  post {
    failure {
      withCredentials([file(credentialsId: 'kubeconfig-tasks', variable: 'KUBECFG')]) {
        sh '''#!/usr/bin/env bash
set -e
export KUBECONFIG="${KUBECFG}"
kubectl get pods -A || true
        '''
      }
    }
  }
}
