def call(args) {
  properties([
  parameters([
    // Core
    choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Target environment'),
    choice(
      name: 'PIPELINE_MODE',
      choices: ['build-only', 'deploy-only', 'full-cicd'],
      description: 'Pipeline mode'
    ),

    // Build
    string(name: 'REGISTRY_URL', defaultValue: '', description: 'Harbor registry URL'),
    string(name: 'IMAGE_NAME', defaultValue: '', description: 'Image name'),
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Image tag'),
    string(name: 'DOCKERFILE_PATH', defaultValue: 'Dockerfile', description: 'Path to Dockerfile(Default: Dockerfile)'),

    // Deploy

    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Image tag'),
    string(name: 'DOCKERFILE_PATH', defaultValue: 'Dockerfile', description: 'Path to Dockerfile(Default: Dockerfile)'),
  ])
])
  repoUrl = args.repoUrl ?: error('REPO_URL is required')
  // Will hold prepared config across nodes
  def podYaml = """
apiVersion: v1
kind: Pod
metadata:
  name: agent-pod
spec:
  containers:
  - name: agent-container
    image: jenkins/inbound-agent:latest
    imagePullPolicy: Always
    command:
    - cat
    tty: true
    volumeMounts:
    - name: shared
      mountPath: /jenkins-agent
  - name: maven
    image: maven:3.9.8-eclipse-temurin-17
    imagePullPolicy: Always
    command:
    - cat
    tty: true
    resources:
      requests:
        ephemeral-storage: "512Mi"
      limits:
        ephemeral-storage: "1Gi"
  - name: nodejs
    image: node:20
    imagePullPolicy: Always
    command:
    - cat
    tty: true
  - name: golang
    image: golang:1.23
    imagePullPolicy: Always
    command:
    - cat
    tty: true
  - name: sonarqube
    image: sonarsource/sonar-scanner-cli:latest
    imagePullPolicy: Always
    command:
    - cat
    tty: true
    resources:
      requests:
        cpu: "500m"
        memory: "2Gi"
        ephemeral-storage: "512Mi"
      limits:
        cpu: "1000m"
        memory: "4Gi"
        ephemeral-storage: "1Gi"
  - name: trivy
    image: aquasec/trivy:0.54.1
    imagePullPolicy: Always
    command:
    - cat
    tty: true
    volumeMounts:
      - name: shared
        mountPath: /jenkins-agent
      - name: harbor-secret
        mountPath: /root/.docker
    resources:
      requests:
        cpu: "200m"
        memory: "1Gi"
        ephemeral-storage: "2Gi"
      limits:
        cpu: "500m"
        memory: "2Gi"
        ephemeral-storage: "4Gi"
  - name: kubectl
    image: alpine/k8s:1.31.12
    imagePullPolicy: Always
    command:
    - cat
    tty: true
  - name: helm
    image: alpine/helm:3.15.4
    imagePullPolicy: Always
    command:
    - cat
    tty: true
  - name: buildkit
    image: moby/buildkit:latest
    tty: true
    command: ['cat',]
    securityContext:
      privileged: true
    volumeMounts:
    - name: shared
      mountPath: /jenkins-agent
    - name: harbor-secret
      mountPath: /root/.docker
  volumes:
  - name: shared
    emptyDir: {}
  - name: harbor-secret
    secret: 
      secretName: harbor-registry
      items:
      - key: .dockerconfigjson
        path: config.json
"""
  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    stage('Checkout') {
      echo 'Checkout code from repository...'
    }

    stage('Prepare Parameters') {
      echo 'Preparing parameters...'
    }

    stage('Prepare Agent') {
      echo 'Preparing agent for execution...'
    }
  }

  //------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: podYaml) {
    node(POD_LABEL) {
      stage('Checkout') {
        echo 'Checkout code from repository...'
        git url: 'https://github.com/phu-phurithat/Boardgame.git', branch: 'main'
      }
      stage('Compile&Scan source code') {
        container('maven') {
          withCredentials([string(credentialsId: 'java_sonar', variable: 'JAVA_TOKEN')]) {
            def SONAR_HOST = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
            def SONAR_PROJECT_KEY = 'java'
            sh """
                  mvn clean install verify sonar:sonar \
                  -Dsonar.host.url=${SONAR_HOST} \
                  -Dsonar.login=${JAVA_TOKEN} \
                  -Dsonar.projectKey=${SONAR_PROJECT_KEY}
                  """
          }
        }
      }
      stage('Build Docker Image') {
            container('buildkit') {
             sh """
             buildctl \
      --addr tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234 \
      build \
      --frontend dockerfile.v0 \
      --local context=. \
      --local dockerfile=. \
      --output type=image,name=harbor.phurithat.site/boardgame_1/boardgame:latest,push=true,registry.config=/root/.docker/config.json \
      --export-cache type=inline \
      --import-cache type=registry,ref=harbor.phurithat.site/boardgame_1/boardgame:latest
      """
            }
      }
      stage('Image Scan') {
        container('trivy') {
          sh  'trivy image --severity HIGH,CRITICAL harbor.phurithat.site/boardgame_1/boardgame:latest'
        }
      }
      stage('Deploy') {
        container('kubectl') {
          echo 'Deploying application to Kubernetes...'
          def kubeconfigFile = "${env.WORKSPACE}/kubeconfig"
          withCredentials([file(credentialsId: 'boardgame-kubeconfig', variable: 'KUBECONFIG_FILE')])  {
            sh '''
          kubectl --kubeconfig=${KUBECONFIG_FILE} apply -k k8s/
        '''
          }
        }
      }
    }
  }
}
