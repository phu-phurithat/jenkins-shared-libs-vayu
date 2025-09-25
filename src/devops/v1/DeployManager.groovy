package devops.v1

def deployHelm(args) {
  // @param args: Map with keys:
  //  kubeconfigCred: String - Jenkins credential ID for kubeconfig file
  //  namespace: String - Kubernetes namespace to deploy into
  //  helmPath: String - Path to the Helm chart directory
  //  helmRelease: String - (optional) Helm release name; defaults to 'my-app'

  String kubeconfigCred = args.kubeconfigCred
  String NAMESPACE    = args.namespace
  String VALUE_PATH   = args.valuePath
  String HELM_RELEASE = args.helmRelease

  container('helm') {
      stage('Helm Dry-Run') {
        try {
          withCredentials([file(credentialsId: args.kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
            sh """
            export KUBECONFIG=${KUBECONFIG_FILE}
            helm upgrade --install ${HELM_RELEASE} stable/app \
              -f ${VALUE_PATH} \
              --namespace ${NAMESPACE} \
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
          helm upgrade --install ${HELM_RELEASE} stable/app \
            -f ${VALUE_PATH} \
            --namespace ${NAMESPACE} \
            --wait --timeout 30s
          """
          }
        } catch (Exception e) {
          error "Deployment failed: ${e}"
        }
      }
  }
}

def rollbackHelm(helmRelease, namespace, kubeconfigCred) {
  // @param helmRelease: String - Helm release name
  // @param namespace: String - Kubernetes namespace
  // @param kubeconfigCred: String - Jenkins credential ID for kubeconfig file

  container('helm') {
    try {
      withCredentials([file(credentialsId: kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
        String previousRevision = getPreviousHelmRevision(helmRelease, namespace, kubeconfigCred)

        if (previousRevision) {
          sh """
            export KUBECONFIG=${KUBECONFIG_FILE}
            helm rollback ${helmRelease} ${lastRevision} -n ${namespace}
          """
          echo "Rolled back Helm release ${helmRelease} to revision ${lastRevision} in namespace ${namespace}"
          currentBuild.result = 'UNSTABLE'
        } else {
          echo "No previous Helm revision found for release ${helmRelease} in namespace ${namespace}"
          echo "Uninstalling release ${helmRelease} in namespace ${namespace} ..."
          sh """
            export KUBECONFIG=${KUBECONFIG_FILE}
            helm uninstall ${helmRelease} -n ${namespace}
          """
          currentBuild.result = 'FAILURE'
        }
      }
    } catch (Exception e) {
      error "Helm rollback failed: ${e}"
    }
  }
}
def getPreviousHelmRevision(helmRelease, namespace, kubeconfigCred) {
  // Returns the previous revision number for a Helm release using helm status
  container('helm') {
    withCredentials([file(credentialsId: kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
      String statusOutput = sh(
        script: """
          export KUBECONFIG=${KUBECONFIG_FILE}
          helm status ${helmRelease} -n ${namespace} --output json
        """,
        returnStdout: true
      ).trim()
      def statusJson = readJSON text: statusOutput
      int currentRevision = statusJson?.revision ?: 1
      return (currentRevision > 1) ? (currentRevision - 1).toString() : null
    }
  }
}

def updateHelmValuesFile(valuePath, imageFullName) {
  // @param valuePath: String - Path to Helm values.yaml file
  // @param imageFullName: String - Full image name with tag to set in values.yaml

  if (!fileExists(valuePath)) {
    error "Helm values file not found at ${valuePath}"
  }

  def helmValues = readYaml file: valuePath
  if (!helmValues.image) {
    helmValues.image = [:]
  }
  helmValues.image.repository = imageFullName.tokenize(':')[0]
  helmValues.image.tag = imageFullName.tokenize(':')[1]

  writeYaml file: valuePath, data: helmValues , overwrite: true
  echo "Updated Helm values file at ${valuePath} with image ${imageFullName}"
}
