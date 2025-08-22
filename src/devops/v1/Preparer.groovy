package devops.v1

class Preparer implements Serializable {

  private static final long serialVersionUID = 1L

  def config
  String componentName
  Map args = [:]

  Preparer(Map args = [:]) {
    this.args = args ?: [:]
    if (!this.args.DEPLOYMENT_REPO) {
      error "Deployment repository is not provided."
    }
    if (!this.args.TRIGGER_TOKEN) {
      echo "Trigger token is not provided, using default."
    }
  }

  def getConfig(String repo) {
    if (!repo) {
      error "Repository is not provided."
    }

    echo "Reading configurations from ${repo}"
    componentName = repo.tokenize('/').last().replaceFirst(~/\.git$/, '')

    String workspace = env.WORKSPACE ?: ''
    String prefix = workspace.endsWith('/') ? workspace : workspace + '/'
    String configPath = "${prefix}${componentName}/config.yaml"

    String configContent = readFile(file: configPath, encoding: 'UTF-8')

    if (configContent?.trim()) {
      config = readYaml(text: configContent)
      echo "Configuration loaded for component: ${componentName}"
    } else {
      error "Configuration file not found or empty."
    }

    echo "Successfully read configuration for ${componentName}"
    return config
  }
}