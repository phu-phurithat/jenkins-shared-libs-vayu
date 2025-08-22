package devops.v1

class Preparer implements Serializable {

  def config
  String COMPONENT_NAME

  def globalENV(){
    env.TRIVY_BASE_URL = "http://trivy.trivy-system.svc.cluster.local:4954"
    env.DEFECTDOJO_BASE_URL = "http://defectdojo-django.defectdojo.svc.cluster.local"
    env.DOJO_KEY = "defect-dojo-key"
    env.HARBOR_CRED = "harbor-cred"
    env.JENKINS_CRED = "jenkins-cred"
    env.GITLAB_NONPROD_KEY = "gitlab-nonprod-key"
    env.GITLAB_PROD_KEY = "gitlab-prod-key"
    env.HELM_PATH = "vayu-helm"
    env.HELM_NONPROD_REPO = "https://gitlab.devopsnonprd.vayuktbcs/api/v4/projects/7410/packages/helm/stable"
    env.OCP_NONPROD_AGENT = "ocp-nonprod-agent"
    env.OCP_PROD_AGENT = "ocp-prod-agent"
  }

  Preparer Preparer(args){

    this.args = args ?: [:]
    if (!this.args.DEPLOYMENT_REPO) {
      error "Deployment repository is not provided."
    }
    if (!this.args.TRIGGER_TOKEN) {
      echo "Trigger token is not provided, using default."
    }

    readConfigs(args.DEPLOYMENT_REPO)

  }

  def readConfigs(repo) {
    // Logic to read configurations from the repository
    // This is a placeholder for actual implementation
    echo "Reading configurations from ${repo}"
    COMPONENT_NAME = repo.tokenize('/').last().replace('.git', '')
    String configPath = env.WORKSPACE + COMPONENT_NAME '/config.yaml'
    configContent = readFile(file: 'configPath', encoding: 'UTF-8')

    if (configContent) {
      config = readYaml text: configContent
      echo "Configuration loaded for component: ${COMPONENT_NAME}"
    } else {
      error "Configuration file not found or empty."
    }

    echo "Configuration: ${config}"
  }
}