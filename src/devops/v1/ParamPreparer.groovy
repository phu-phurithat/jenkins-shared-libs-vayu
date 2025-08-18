package devops.v1

class ParamPreparer implements Serializable {

  String repoUrl
  String env
  String pipelineMode
  String registryUrl
  String imageName
  String imageTag
  String dockerfilePath

  void prepareParams() {
    this.repoUrl = params.get('REPO_URL', this.repoUrl)
    this.env = params.get('ENV', this.env)
    this.pipelineMode = params.get('PIPELINE_MODE', this.pipelineMode)
    this.registryUrl = params.get('REGISTRY_URL', this.registryUrl)
    this.imageName = params.get('IMAGE_NAME', this.imageName)
    this.imageTag = params.get('IMAGE_TAG', this.imageTag)
    this.dockerfilePath = params.get('DOCKERFILE_PATH', this.dockerfilePath)
  }

  void validateParams() {
    if (!repoUrl) {
      error('REPO_URL is required')
    }
    if (!env) {
      error('ENV is required')
    }
    if (!pipelineMode) {
      error('PIPELINE_MODE is required')
    }
    if (!registryUrl) {
      error('REGISTRY_URL is required')
    }
    if (!imageName) {
      error('IMAGE_NAME is required')
    }
    if (!imageTag) {
      error('IMAGE_TAG is required')
    }
  }
}