pipeline {
  agent any

  environment {
    SPRING_REPO = "https://github.com/i-ajaj/spring-boot.git"
    WORKER_REPO = "https://github.com/i-ajaj/python-worker.git"

    SPRING_IMAGE = "spring-boot-app"
    WORKER_IMAGE = "python-worker-app"

    KIND_CLUSTER  = "tasks" 
    K8S_NAMESPACE = "tasks-app"

    // A workspace-local kubeconfig path
    KUBECONFIG_PATH = "${env.WORKSPACE}/.kube/config"
    CHARTS_ROOT = "/home/azureuser/charts"

    // Stop helm/kubectl from going through Jenkins/http proxy
    NO_PROXY_ALL = "localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,.svc,.cluster.local"
  }

  options { timestamps() }

  stages {

    stage('Preflight') {
      steps {
        sh '''#!/usr/bin/env bash
set -euo pipefail
echo "== Tool versions =="
ls
command -v docker
command -v kind
command -v kubectl
command -v helm
docker version --format '{{.Server.Version}}' || true
kind get clusters || true
helm version || true

echo "== Current proxy-related env =="
env | egrep -i '(^|_)http_proxy=|(^|_)https_proxy=|(^|_)no_proxy=' || true
        '''
      }
    }

    stage('Cloning Repos') {
      steps {
        script {
          def isManual = currentBuild.getBuildCauses().any { it.toString().contains("UserIdCause") }
          echo isManual ? "Manual build detected — cloning both repos" : "Webhook triggered by repo: ${env.REPO_NAME}"

          dir('spring-boot') {
              if (fileExists('.git')) {
                  echo "Fetching only changes from sprin-boot repo..."
                  sh '''
                    git fetch origin main
                    git reset --hard origin/main
                  '''
              } else {
                  echo "Shallow cloning spring-boot..."
                  sh 'git clone --depth=1 --branch=main ${SPRING_REPO} .'
              }
          }

          dir('python-worker') {
              if (fileExists('.git')) {
                  echo "Fetching only changes from python-worker repo..."
                  sh '''
                    git fetch origin main
                    git reset --hard origin/main
                  '''
              } else {
                  echo "Shallow cloning python-worker..."
                  sh 'git clone --depth=1 --branch=main ${WORKER_REPO} .'
              }
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
        script { env.APP_VERSION = "${env.BUILD_NUMBER}" }
        sh '''#!/usr/bin/env bash
set -euo pipefail
echo "Using version: ${APP_VERSION}"
docker tag ${SPRING_IMAGE}:latest ${SPRING_IMAGE}:${APP_VERSION}
docker tag ${WORKER_IMAGE}:latest ${WORKER_IMAGE}:${APP_VERSION}
kind load docker-image ${SPRING_IMAGE}:${APP_VERSION} --name ${KIND_CLUSTER}
kind load docker-image ${WORKER_IMAGE}:${APP_VERSION} --name ${KIND_CLUSTER}
docker image ls | grep -E "${SPRING_IMAGE}|${WORKER_IMAGE}" || true
        '''
      }
    }

    // NEW: make a kubeconfig and verify connectivity with proxies disabled
    stage('Kubeconfig & Cluster Check') {
      steps {
        sh '''#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$(dirname "${KUBECONFIG_PATH}")"
kind get kubeconfig --name "${KIND_CLUSTER}" > "${KUBECONFIG_PATH}"
chmod 600 "${KUBECONFIG_PATH}"

# Nuke all proxies for this shell, and set a broad NO_PROXY
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
export NO_PROXY="${NO_PROXY_ALL}"

echo "== kubectl context =="
kubectl --kubeconfig="${KUBECONFIG_PATH}" config current-context || true
kubectl --kubeconfig="${KUBECONFIG_PATH}" cluster-info
kubectl --kubeconfig="${KUBECONFIG_PATH}" get nodes -o wide

# Ensure namespace exists
kubectl --kubeconfig="${KUBECONFIG_PATH}" create namespace "${K8S_NAMESPACE}" --dry-run=client -o yaml | \
  kubectl --kubeconfig="${KUBECONFIG_PATH}" apply -f -
        '''
      }
    }

stage('Helm Deploy (versioned)') {
  steps {
    sh '''#!/usr/bin/env bash
set -euo pipefail

unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
export NO_PROXY="${NO_PROXY_ALL}"


echo "== Looking for Helm charts =="
pwd
echo "CHARTS_DIR=${CHARTS_ROOT}"

if [ -d "${CHARTS_ROOT%/}/spring-app" ] && [ -d "${CHARTS_ROOT%/}/python-worker" ]; then
  echo "Charts found. Deploying with Helm..."

  helm upgrade --install spring-app ${CHARTS_ROOT}/spring-app \
    --kubeconfig "${KUBECONFIG_PATH}" \
    --namespace ${K8S_NAMESPACE} \
    --set image.repository=${SPRING_IMAGE} \
    --set image.tag=${APP_VERSION} \
    --set image.pullPolicy=IfNotPresent \
    --wait --timeout 5m
  
  helm upgrade --install python-worker ${CHARTS_ROOT}/python-worker \
    --kubeconfig "${KUBECONFIG_PATH}" \
    --namespace ${K8S_NAMESPACE} \
    --set image.repository=${WORKER_IMAGE} \
    --set image.tag=${APP_VERSION} \
    --set image.pullPolicy=IfNotPresent \
    --wait --timeout 5m
fi

echo "=== Helm Releases (if any) ==="
helm list -n ${K8S_NAMESPACE} --kubeconfig "${KUBECONFIG_PATH}" || true

echo "=== Pods ==="
kubectl get pods -n ${K8S_NAMESPACE} -o wide --kubeconfig "${KUBECONFIG_PATH}"
'''
  }
}

  }

  post {
    failure {
      sh '''#!/usr/bin/env bash
set -e
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
export NO_PROXY="${NO_PROXY_ALL}"
kubectl get pods -A --kubeconfig "${KUBECONFIG_PATH}" || true
      '''
    }
  }
}
