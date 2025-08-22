package devops.v1

class Preparer implements Serializable {

  def config
  String componentName
  Map args = [:]

  Preparer(args) {
    this.args = args ?: [:]
  }

  def getConfig(String workspace,String repo) {
    componentName = repo.tokenize('/').last().replaceFirst(~/\.git$/, '')

    String prefix = workspace.endsWith('/') ? workspace : workspace + '/'
    String configPath = "${prefix}${componentName}/config.yaml"

    String configContent = readFile(file: configPath, encoding: 'UTF-8')

    if (configContent?.trim()) {
      config = readYaml(text: configContent)
    } else {
    }
    return config
  }

  def getConfigSummary() {
    return """
============== Configuration Summary =====================

------------------- Build Section ------------------------
Component Name: ${componentName}
Build Tool: ${config.build_tool ?: 'None'}
Build Image: ${config.build_image ?: 'None'}

-------------------- Security Section --------------------
Code Quality : ${config.security.code ? '✅' : '❌'}
Secret Scanning : ${config.security.secret ? '✅' : '❌'}
Dependency Scanning : ${config.security.dependency ? '✅' : '❌'}
Image Scanning : ${config.security.image ? '✅' : '❌'}
End Of Life Scanning : ${config.security.eol ? '✅' : '❌'}
DAST Scanning : ${config.security.dast ? '✅' : '❌'}

------------------- Deployment Section --------------------
...

===========================================================
"""
  }

}
