package devops.v1

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
          withCredentials([file(credentialsId: args.kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
            sh """
            export KUBECONFIG=${KUBECONFIG_FILE}
            helm upgrade --install ${HELM_RELEASE} stable/app \
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
          helm upgrade --install ${HELM_RELEASE} stable/app \
            --namespace ${NAMESPACE} \
            --create-namespace \
            --wait --timeout 5m
          """
          }
        } catch (Exception e) {
          rollbackHelm(HELM_RELEASE, NAMESPACE)
          error "Helm deploy failed: ${e} - Rolled back to previous release"
        }
      }
  }
}

private rollbackHelm(helmRelease, namespace) {
  // @param helmRelease: String - Helm release name
  // @param namespace: String - Kubernetes namespace

  container('helm') {
    try {
      withCredentials([file(credentialsId: 'kubeconfig-nonprod', variable: 'KUBECONFIG_FILE')]) {
        int lastRevision = sh(
          script: "helm history ${helmRelease} -n ${namespace} --max 1 | awk 'NR==2 {print \$1}'",
          returnStdout: true
        ).trim()

        if (lastRevision) {
          sh """
            export KUBECONFIG=${KUBECONFIG_FILE}
            helm rollback ${helmRelease} ${lastRevision} -n ${namespace}
          """
          echo "Rolled back Helm release ${helmRelease} to revision ${lastRevision} in namespace ${namespace}"
        } else {
          echo "No previous Helm revision found for release ${helmRelease} in namespace ${namespace}"
        }
      }
    } catch (Exception e) {
      error "Helm rollback failed: ${e}"
    }
  }
}

def updateHelmValuesFile(helmValuesPath, imageFullName) {
  // @param helmValuesPath: String - Path to Helm values.yaml file
  // @param imageFullName: String - Full image name with tag to set in values.yaml

  if (!fileExists(helmValuesPath)) {
    error "Helm values file not found at ${helmValuesPath}"
  }

  def helmValues = readYaml file: helmValuesPath
  if (!helmValues.image) {
    helmValues.image = [:]
  }
  helmValues.image.repository = imageFullName.tokenize(':')[0]
  helmValues.image.tag = imageFullName.tokenize(':')[1]

  writeYaml file: helmValuesPath, data: helmValues
  echo "Updated Helm values file at ${helmValuesPath} with image ${imageFullName}"
}

