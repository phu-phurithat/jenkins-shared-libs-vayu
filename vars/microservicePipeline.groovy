import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed

    def prep = new Preparer(args)
    def credManager = new CredManager()
    def podTemplate = new PodTemplate()
    def config = [:]

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

  //------------------- Run inside Kubernetes podTemplate -------------------
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
            sh '''
              buildctl \
              --addr tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234 \
              build \
              --frontend dockerfile.v0 \
              --local context=. \
              --local dockerfile=. \
              --output type=image,name=harbor.phurithat.site/boardgame_1/boardgame:latest,push=true,registry.config=/root/.docker/config.json \
              --export-cache type=inline \
              --import-cache type=registry,ref=harbor.phurithat.site/boardgame_1/boardgame:latest
              '''
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
