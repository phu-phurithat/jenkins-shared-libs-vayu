package devops.v1

class Preparer implements Serializable {

  def config
  String componentName

  Preparer Preparer(args){

    this.args = args ?: [:]
    if (!this.args.DEPLOYMENT_REPO) {
      error "Deployment repository is not provided."
    }
    if (!this.args.TRIGGER_TOKEN) {
      echo "Trigger token is not provided, using default."
    }
  }

  def getConfig(repo) {
    echo "Reading configurations from ${repo}"
    componentName = repo.tokenize('/').last().replace('.git', '')
    String configPath = env.WORKSPACE + componentName '/config.yaml'
    configContent = readFile(file: 'configPath', encoding: 'UTF-8')

    if (configContent) {
      config = readYaml text: configContent
      echo "Configuration loaded for component: ${COMPONENT_NAME}"
    } else {
      error "Configuration file not found or empty."
    }
    ecgo "Successfully read configuration for ${COMPONENT_NAME}"
    return config
  }
}