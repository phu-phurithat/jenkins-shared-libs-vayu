import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String REGISTRY          = 'harbor.phurithat.site'
  final String REPO              = 'kudesphere'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'
  final String APP_REPO          = args.DEPLOYMENT_REPO.replace('-helm-charts.git', '-app.git')
  final String COMPONENT_NAME    = args.DEPLOYMENT_REPO.tokenize('/').last().replace('-helm-charts.git', '')

  // ENV
  env.TRIVY_BASE_URL = 'http://trivy.trivy-system.svc.cluster.local:4954'
  env.DEFECTDOJO_BASE_URL = 'http://defectdojo-django.defectdojo.svc.cluster.local'
  env.DOJO_KEY = 'defect-dojo-key'
  env.HARBOR_CRED = 'harbor-cred'
  env.JENKINS_CRED = 'jenkins-cred'
  env.GITLAB_NONPROD_KEY = 'gitlab-nonprod-key'
  env.GITLAB_PROD_KEY = 'gitlab-prod-key'
  env.HELM_PATH = 'vayu-helm'
  env.HELM_NONPROD_REPO = 'https://gitlab.devopsnonprd.vayuktbcs/api/v4/projects/7410/packages/helm/stable'
  env.OCP_NONPROD_AGENT = 'ocp-nonprod-agent'
  env.OCP_PROD_AGENT = 'ocp-prod-agent'

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    // Basic input validation
    if (!args?.DEPLOYMENT_REPO) {
      error 'DEPLOYMENT_REPO is required'
    }

    def prep        = new Preparer(args)
    // def credManager = new CredManager(this)
    def pt = new PodTemplate()
    def config      = [:]

    stage('Checkout') {
      echo 'Checkout code from repository...'
      git url: args.DEPLOYMENT_REPO, branch: 'master'
    }

    stage('Read Configuration from /"config.yaml/"') {
      echo 'Getting Configuration'
      String configPath = "${env.WORKSPACE}/config.yaml"
      String configContent = readFile(file: configPath, encoding: 'UTF-8')

      if (configContent?.trim()) {
        config = readYaml(text: configContent)
      } else {
        error "Configuration file not found or empty at ${configPath}"
      }
      prep.injectConfig(config)
      // credManager.globalENV()
      echo prep.getConfigSummary()
    }

    stage('Prepare Agent') {
      pt.injectConfig(config)
    }
  }

  // ------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: pt.toString()) {
    node(POD_LABEL) {
      stage('Checkout') {
        echo 'Checkout code from repository...'
        String appRepo = args.DEPLOYMENT_REPO.replace('-helm-charts.git', '-app.git')
        git url: appRepo, branch: 'master'
      }

      // stage('Compile&Scan source code') {
      //   container('maven') {
      //     withCredentials([string(credentialsId: 'java_sonar', variable: 'JAVA_TOKEN')]) {
      //       sh """
      //         mvn clean install verify sonar:sonar \
      //           -Dsonar.host.url=${SONAR_HOST} \
      //           -Dsonar.login=${JAVA_TOKEN} \
      //           -Dsonar.projectKey=${SONAR_PROJECT_KEY}
      //       """
      //     }
      //   }
      // }

      stage('Build Docker Image') {
        String imageTag = 'latest'
        container('buildkit') {
          sh """
            buildctl \
              --addr ${BUILDKIT_ADDR} \
              build \
              --frontend dockerfile.v0 \
              --local context=. \
              --local dockerfile=. \
              --output type=image,\
name=${REGISTRY}/${REPO}/${componentName}:${imageTag},\
push=true,\
registry.config=${DOCKER_CONFIG} \
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

    // stage('Deploy') {
    //   container('kubectl') {
    //     echo 'Deploying application to Kubernetes...'
    //     withCredentials([file(credentialsId: 'boardgame-kubeconfig', variable: 'KUBECONFIG_FILE')]) {
    //       sh 'kubectl --kubeconfig=${KUBECONFIG_FILE} apply -k k8s/'
    //     }
    //   }
    // }
    }
  }
}
