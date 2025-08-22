package devops.v1

class Preparer implements Serializable {

  def config
  String componentName
  Map args = [:]

  Preparer(args) {
    this.args = args ?: [:]
  }

  def injectConfig(config) {
    this.config = config
    return this
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
