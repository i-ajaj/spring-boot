pipeline {
  agent any

  environment {
    SPRING_REPO   = "https://github.com/i-ajaj/spring-boot.git"
    WORKER_REPO   = "https://github.com/i-ajaj/python-worker.git"

    SPRING_IMAGE  = "spring-boot-app"
    WORKER_IMAGE  = "python-worker-app"

    KIND_CLUSTER  = "tasks"
    K8S_NAMESPACE = "tasks-app"

    // workspace-local kubeconfig path
    KUBECONFIG_PATH = "${env.WORKSPACE}/.kube/config"

    // local charts folder not needed anymore for deploys, but kept if you still use it elsewhere
    CHARTS_ROOT = "/home/azureuser/charts"

    // keep kubectl/argocd off proxies
    NO_PROXY_ALL = "localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,.svc,.cluster.local"
  }

  options { timestamps() }

  stages {
    stage('Cloning Repos') {
      steps {
        script {
          def isManual = currentBuild.getBuildCauses().any { it.toString().contains("UserIdCause") }
          echo isManual ? "Manual build detected — cloning both repos" : "Webhook triggered by repo: ${env.REPO_NAME}"

          dir('spring-boot') {
            if (fileExists('.git')) {
              echo "Fetching only changes from spring-boot..."
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
              echo "Fetching only changes from python-worker..."
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
        '''
      }
    }

    stage('Kubeconfig & Cluster Check') {
      steps {
        sh '''#!/usr/bin/env bash
          set -euo pipefail
          mkdir -p "$(dirname "${KUBECONFIG_PATH}")"
          kind get kubeconfig --name "${KIND_CLUSTER}" > "${KUBECONFIG_PATH}"
          chmod 600 "${KUBECONFIG_PATH}"
          
          # disable proxies for this shell
          unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
          export NO_PROXY="${NO_PROXY_ALL}"
          export KUBECONFIG="${KUBECONFIG_PATH}"
          
          echo "== kubectl context =="
          kubectl config current-context || true
          kubectl cluster-info
          kubectl get nodes -o wide
          
          # ensure namespace exists
          kubectl create namespace "${K8S_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
        '''
      }
    }

    stage('Argo CD Deploy') {
      environment {
        ARGOCD_LOCAL_PORT = "8080"   // we use HTTP on 80 -> --plaintext
      }
      steps {
        sh '''#!/usr/bin/env bash
          set -euo pipefail
          
          unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
          export NO_PROXY="${NO_PROXY_ALL}"
          export KUBECONFIG="${KUBECONFIG_PATH}"
          
          echo "== Start port-forward (svc/argocd-server 80 -> localhost:${ARGOCD_LOCAL_PORT}) =="
          # kill any previous forward
          pkill -f 'port-forward.*argocd-server' || true
          kubectl -n argocd port-forward svc/argocd-server ${ARGOCD_LOCAL_PORT}:80 \
            >/tmp/argocd-pf.log 2>&1 & echo $! >/tmp/argocd-pf.pid
          
          # wait for socket readiness (no netcat needed)
          for i in {1..60}; do
            (exec 3<>/dev/tcp/127.0.0.1/${ARGOCD_LOCAL_PORT}) >/dev/null 2>&1 && { exec 3>&-; break; } || sleep 0.5
          done
          
          echo "== Obtain Argo CD admin password (initial secret) =="
          ARGOCD_PWD="$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' 2>/dev/null | base64 -d || true)"
          if [ -z "${ARGOCD_PWD}" ]; then
            echo "Could not read 'argocd-initial-admin-secret'. If you've changed admin password, set ARGOCD_PASSWORD env or create a Jenkins credential."
            exit 1
          fi
          
          echo "== argocd login =="
          argocd login "localhost:${ARGOCD_LOCAL_PORT}" --username admin --password "${ARGOCD_PWD}" --plaintext
          
          echo "== Create/Upsert Argo CD Applications =="
          # spring-app
          argocd app create spring-app --upsert \
            --repo ${SPRING_REPO} \
            --path charts/spring-app \
            --revision main \
            --dest-namespace ${K8S_NAMESPACE} \
            --dest-server https://kubernetes.default.svc \
            --helm-release-name spring-app
          
          # python-worker
          argocd app create python-worker --upsert \
            --repo ${WORKER_REPO} \
            --path charts/python-worker \
            --revision main \
            --dest-namespace ${K8S_NAMESPACE} \
            --dest-server https://kubernetes.default.svc \
            --helm-release-name python-worker
          
          echo "== Set build image values on both apps =="
          argocd app set spring-app \
            --helm-set image.repository=${SPRING_IMAGE} \
            --helm-set image.tag=${APP_VERSION} \
            --helm-set image.pullPolicy=IfNotPresent
          
          argocd app set python-worker \
            --helm-set image.repository=${WORKER_IMAGE} \
            --helm-set image.tag=${APP_VERSION} \
            --helm-set image.pullPolicy=IfNotPresent
          
          echo "== Sync and wait healthy =="
          argocd app sync spring-app --prune
          argocd app sync python-worker --prune
          
          argocd app wait spring-app --timeout 300 --health --sync
          argocd app wait python-worker --timeout 300 --health --sync
          
          echo "== Deployed images =="
          kubectl -n ${K8S_NAMESPACE} get deploy spring-app -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
          kubectl -n ${K8S_NAMESPACE} get deploy python-worker -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
          
          echo "== Argo CD apps summary =="
          argocd app list
        '''
      }
      post {
        always {
          sh '''#!/usr/bin/env bash
            set -e
            # stop port-forward
            if [ -f /tmp/argocd-pf.pid ]; then kill $(cat /tmp/argocd-pf.pid) 2>/dev/null || true; fi
            echo "---- /tmp/argocd-pf.log (tail) ----"
            tail -n 100 /tmp/argocd-pf.log 2>/dev/null || true
          '''
        }
      }
    }

  } // stages

  post {
    always {
      sh '''#!/usr/bin/env bash
        set -e
        unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
        export NO_PROXY="${NO_PROXY_ALL}"
        export KUBECONFIG="${KUBECONFIG_PATH}"
        echo "=== Pods (post) ==="
        kubectl get pods -n ${K8S_NAMESPACE} -o wide || true
      '''
      cleanWs()
    }
    failure {
      sh '''#!/usr/bin/env bash
        set -e
        unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
        export NO_PROXY="${NO_PROXY_ALL}"
        export KUBECONFIG="${KUBECONFIG_PATH}"
        kubectl get pods -A || true
      '''
    }
  }
}
