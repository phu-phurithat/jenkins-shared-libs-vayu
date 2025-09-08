def deployHelm(args) {
  // @param args: Map with keys:
  //  kubeconfigCred: String - Jenkins credential ID for kubeconfig file
  //  namespace: String - Kubernetes namespace to deploy into
  //  helmPath: String - Path to the Helm chart directory
  //  helmRelease: String - (optional) Helm release name; defaults to 'my-app'

  String kubeconfigCred = args.kubeconfigCred
  String NAMESPACE    = args.namespace
  String HELM_PATH   = args.helmPath
  String HELM_RELEASE = args.helmRelease

  container('helm') {
      stage('Helm Lint & Dry-Run') {
        try {
          sh "helm lint ${args.helmPath}"
          withCredentials([file(credentialsId: args.kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
            sh """
            export KUBECONFIG=${KUBECONFIG_FILE}
            helm upgrade --install ${HELM_RELEASE} ${HELM_PATH} \
              --namespace ${NAMESPACE} \
              --create-namespace \
              --dry-run=client
          """
          }
        } catch (Exception e) {
          error "Helm dry-run failed: ${e}"
        }
      }

      stage('Deploy via Helm') {
        try {
          withCredentials([file(credentialsId: kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
            sh """
          export KUBECONFIG=${KUBECONFIG_FILE}
          helm upgrade --install ${HELM_RELEASE} ${HELM_PATH} \
            --namespace ${NAMESPACE} \
            --create-namespace \
            --wait --timeout 5m
          """
          }
        } catch (Exception e) {
          error "Helm deploy failed: ${e}"
        }
      }
  }
}

