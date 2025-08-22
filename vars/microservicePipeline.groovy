import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String REGISTRY         = 'harbor.phurithat.site/boardgame_1/boardgame'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    // Basic input validation
    if (!args?.DEPLOYMENT_REPO) {
      error "DEPLOYMENT_REPO is required"
    }

    def prep        = new Preparer(this,args)
    def credManager = new CredManager()
    def podTemplate = new PodTemplate()
    def config      = [:]

    stage('Checkout') {
      git url: args.DEPLOYMENT_REPO, branch: 'master'
    }

    stage('Prepare Parameters and Environment Variables') {
      config = prep.getConfig(args.DEPLOYMENT_REPO)
      credManager.globalENV()
    }

    stage('Prepare Agent') {
      podTemplate.injectConfig(config)
    }
  }

  // ------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: podTemplate.toString()) {
    node(POD_LABEL) {

      stage('Checkout') {
        echo 'Checkout code from repository...'
        String appRepo = args.DEPLOYMENT_REPO.replace('-helm-charts.git', '-app.git')
        git url: appRepo, branch: 'master'
      }

      stage('Compile&Scan source code') {
        container('maven') {
          withCredentials([string(credentialsId: 'java_sonar', variable: 'JAVA_TOKEN')]) {
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
        String imageTag = "latest"
        container('buildkit') {
          sh """
            buildctl \
              --addr ${BUILDKIT_ADDR} \
              build \
              --frontend dockerfile.v0 \
              --local context=. \
              --local dockerfile=. \
              --output type=image,name=${REGISTRY}:${imageTag},push=true,registry.config=${DOCKER_CONFIG} \
              --export-cache type=inline \
              --import-cache type=registry,ref=${REGISTRY}
          """
        }
      }

      stage('Image Scan') {
        container('trivy') {
          sh "trivy image --severity HIGH,CRITICAL ${REGISTRY}"
        }
      }

      stage('Deploy') {
        container('kubectl') {
          echo 'Deploying application to Kubernetes...'
          withCredentials([file(credentialsId: 'boardgame-kubeconfig', variable: 'KUBECONFIG_FILE')]) {
            sh 'kubectl --kubeconfig=${KUBECONFIG_FILE} apply -k k8s/'
          }
        }
      }
    }
  }
}
