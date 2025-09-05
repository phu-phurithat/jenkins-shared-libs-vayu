import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN, MICROSERVICE_NAME, BRANCH (optional)

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String REGISTRY          = 'harbor.phurithat.site'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'

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

  // Helper classes
  def pt = new PodTemplate()
  def prep = new Preparer()
  // def credManager = new CredManager(this)
  def config = [:]
  def properties = [:]
  String microserviceRepo = ''

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    // Basic input validation
    prep.validateArguments(args)

    dir ('deployment') {
      stage('Checkout Deployment Repository') {
        git url: args.DEPLOYMENT_REPO, branch: args.BRANCH ?: 'main'
      }

      stage('Read Configuration from "config.yaml"') {
        String configPath = "./config.yaml"
        String configContent = readFile(file: configPath, encoding: 'UTF-8')

        if (configContent?.trim()) {
          config = readYaml(text: configContent)
          // echo config.toString()
        } else {
          error "Configuration file not found or empty at ${configPath}"
        }

        prep.validateConfig(config)
        prep.getConfigSummary(args, config)
      }
    }

    dir('src'){
      stage('Checkout Microservice Code for Preparation') {
        microserviceRepo = config.kinds.deployments[args.MICROSERVICE_NAME]
        if (!microserviceRepo) {
          error "Microservice '${args.MICROSERVICE_NAME}' not found in configuration."
        }
        
        echo "Cloning microservice repository: ${microserviceRepo}"
        git url: microserviceRepo, branch: 'main'
      }

      stage('Read Properties from "devops.properties.yaml"') {
        String propertiesPath = "./devops.properties.yaml"
        if (fileExists(propertiesPath)) {
          def fileContents = readFile(file: propertiesPath, encoding: 'UTF-8')
          properties = readYaml(text: fileContents)
        } else {
          error "Properties file not found at ${propertiesPath}"
        }
      }
    }

      

      stage('Prepare Agent') {
        pt.injectConfig(properties)
      }
}

  // ------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: pt.toString()) {
    node(POD_LABEL) {
      dir('jenkins-agent') {
        stage('Checkout Microservice Code for Build/Deploy') {
          git url: microserviceRepo, branch: args.BRANCH
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

def validateArguments(args) {
  String validateResult = ""
  if (!args?.DEPLOYMENT_REPO) {
    validateResult += 'DEPLOYMENT_REPO is required. '
  }
  if (!args?.TRIGGER_TOKEN) {
    validateResult += 'TRIGGER_TOKEN is required. '
  }
  if (!args?.MICROSERVICE_NAME) {
    validateResult += 'MICROSERVICE_NAME is required. '
  }
  if (validateResult) {
    error "Invalid arguments: ${validateResult}"
  }
}

def validateConfig(config) {
    def errors = []

    // app_id
    if (!(config.app_id instanceof Integer)) {
        errors << "app_id must be an integer"
    }

    // deployments
    if (!(config.kinds?.deployments instanceof Map)) {
        errors << "kinds.deployments must exist and be a map"
    } else {
        config.kinds.deployments.each { name, url ->
            if (!(url instanceof String)) {
                errors << "Deployment '${name}' must have a Git URL string"
            } else if (!url.startsWith("https://github.com/")) {
                errors << "Deployment '${name}' has invalid repo URL: ${url}"
            }
        }
    }

    // environments
    if (!(config.environments instanceof Map)) {
        errors << "environments must exist and be a map"
    } else {
        config.environments.each { env, details ->
            if (!(details instanceof Map)) {
                errors << "Environment '${env}' must be a map"
            } else {
                if (details.cluster && !details.namespace) {
                    errors << "Environment '${env}' must have namespace when cluster is defined"
                }
                if (details.endpoint && !details.credential) {
                    errors << "Environment '${env}' with endpoint must also define credential"
                }
            }
        }
    }

    // registry
    if (!(config.registry instanceof Map)) {
        errors << "registry must exist and be a map"
    } else {
        ["nonprod", "prod"].each { key ->
            if (!(config.registry[key] instanceof String)) {
                errors << "Registry '${key}' must exist"
            } else if (!config.registry[key].startsWith("harbor.")) {
                errors << "Registry '${key}' must point to Harbor: ${config.registry[key]}"
            }
        }
    }

    // helm
    if (!(config.helm instanceof Map)) {
        errors << "helm must exist and be a map"
    } else {
        if (!(config.helm.version ==~ /\d+\.\d+\.\d+/)) {
            errors << "helm.version must be semantic version (e.g., 1.0.0)"
        }
    }

    // Final check
    if (errors) {
        error "❌ Config validation failed:\n - " + errors.join("\n - ")
    } else {
        echo "✅ Config validation passed!"
    }
}
