package devops.v1

def pushChanges(String message, String repoUrl, String credentialsId) {
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh """
            git config user.name "jenkins"
            git config user.email "auto@pipeline.jenkins"
            git add .
            git commit -m "${message}"
        git push origin HEAD:main
    """
    }
}

def setupHelm() {
  container('helm'){
    sh """
    helm repo remove stable || true
    helm repo add stable ${env.HELM_NONPROD_REPO}
    helm repo update
  """
  }
}