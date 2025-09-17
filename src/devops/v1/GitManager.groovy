package devops.v1

def pushChanges(String message, String repoUrl, String credentialsId) {
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
      String sshUrl = repoUrl.replace("https://", "git@").replaceFirst("/", ":")
      sh """
        git config --global user.email "auto@pipeline.jenkins.io"
        git config --global user.name "Jenkins CI"
        git remote set-url origin ${sshUrl}
        git add .
        git commit -m "${message}" || echo "No changes to commit"
        git push origin main
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