properties([
  parameters([
    // Core
    string(name: 'REPO_URL', defaultValue: '', description: 'Git repository URL'),
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

// Will hold prepared config across nodes
def podYaml = """
apiVersion: v1
kind: Pod
metadata:
  name: agent-pod
spec:
  containers:
  - name: maven
    image: maven:3.9.8-eclipse-temurin-17
    imagePullPolicy: Always
    command:
    - cat
    tty: true
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
  - name: trivy
    image: aquasec/trivy:0.54.1
    imagePullPolicy: Always
    command:
    - cat
    tty: true
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
  - name: docker
    image: docker:20.10.7
    command: ['cat']
    tty: true
    volumeMounts:
      - name: docker-sock
        mountPath: /var/run/docker.sock
    resources:
      requests:
        cpu: "100m"
        memory: "128Mi"
        ephemeral-storage: "512Mi"
      limits:
        cpu: "300m"
        memory: "256Mi"
        ephemeral-storage: "1Gi"
  volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
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
      git url: "https://github.com/phu-phurithat/Boardgame.git", branch: 'main'
    }
    stage('Build') {
      echo 'Building Docker image...'
    }
    stage('Security Scan') {
      echo 'Running security scan on Docker image...'
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
