package devops.v1

def ImportReport(fullPath, imageTag, component) {
  String productName = "${fullPath}-${component}"
  String engagementName = "${component}:${imageTag}"
   withCredentials([string(credentialsId: DOJO_KEY, variable: 'DOJO_KEY')]) {

  def productCheck = sh(
                        script: """
                          curl -s -k \
                            "${DEFECTDOJO_BASE_URL}/api/v2/products/?name=${productName}" \
                            -H "Authorization: Token ${DOJO_KEY}" 
                        """,
                        returnStdout: true
                        )
  echo "productCheck: ${productCheck}"
  ///String responseContent = readJSON(text: productId)
  productCheck = readJSON(text: productCheck).results?.isEmpty() ?: null
  echo "productCheck after readJSON: ${productCheck}"
//   def responseProduct = sh(
//     script: """curl -s -k -H "Authorization: Token ${DOJO_KEY}" "${DEFECTDOJO_BASE_URL}/api/v2/products/?name=${productName}" """,
//     returnStdout: true
// )

//   def productId = (responseProduct =~ /"id":\s*(\d+)/)[0][1] ?: null

  if (productCheck) { //null or empty
    echo 'Product not found. Creating...'
    productCheck = sh(
                            script: """
                                curl -s -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/products/" \
                                    -H "Authorization: Token ${DOJO_KEY}" \
                                    -d '{"name": "${productName}", "description": "Auto-created from Jenkins","prod_type": 1}'
                            """,
                            returnStdout: true
                        )
  }

  def engagementCheck = sh(
                        script: """
                            curl -s -k -H "Authorization: Token ${DOJO_KEY}" \
                                 "${DEFECTDOJO_BASE_URL}/api/v2/engagements?name=${engagementName}&product=${productId}"
                        """,
                        returnStdout: true
                       )
  echo "engagementCheck: ${engagementCheck}"
 
  engagementCheck = readJSON(text: engagementCheck).results?.isEmpty() ?: null
  echo "engagementCheck after readJSON: ${engagementCheck}"
  // def responseEngagement = sh(
  //   script: """curl -s -k -H "Authorization: Token ${DOJO_KEY}" "${DEFECTDOJO_BASE_URL}/api/v2/engagements/?name=${engagementName}&product=${productId}" """,
  //   returnStdout: true)
  // def engagementId = (responseEngagement =~ /"id":\s*(\d+)/)[0][1] ?: null
  if (engagementCheck) {
    echo 'Engagement not found. Creating...'
    engagementCheck = sh(
                            script: """
                                curl -s -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/engagements/" \
                                    -H "Authorization: Token ${DOJO_KEY}" \
                                    -H "Content-Type: application/json" \
                                    -d '{
                                        "name": "${engagementName}",
                                        "product": ${productId},
                                        "status": "In Progress",
                                        "engagement_type": "CI/CD"
                                    }'
                            """,
                            returnStdout: true
                        )
  }
 
    //SonarQube Scan Source Code
    sh """

          curl -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/reimport-scan/" \
            -H "Authorization: Token ${DOJO_KEY}" \
            -F scan_type="SonarQube Scan" \
            -F test_title="SonarQube Scan Source Code" \
            -F active="true" \
            -F verified="true" \
            -F file=@sonarqube-report.json \
            -F product_name=${productName} \
            -F engagement_name=${engagementName} \
            -F deduplication_on_engagement=true \
            -F close_old_findings=true \
            -F auto_create_context=true
        """
    //Trivy Scan Dependency
    sh """
          curl -k -X POST "https://defectdojo.phurithat.site/api/v2/reimport-scan/" \
            -H "Authorization: Token ${DOJO_KEY}" \
            -F scan_type="CycloneDX Scan" \
            -F test_title="CycloneDX Scan Dependency" \
            -F active="true" \
            -F verified="true" \
            -F file=@trivy_deps.json \
            -F product_name=${productName} \
            -F engagement_name=${engagementName} \
            -F deduplication_on_engagement=true \
            -F close_old_findings=true \
            -F auto_create_context=trueengagementName
        """

        //Trivy Scan Image
        sh """
          curl -k -X POST "https://defectdojo.phurithat.site/api/v2/reimport-scan/" \
            -H "Authorization: Token ${DOJO_KEY}" \
            -F scan_type="CycloneDX Scan" \
            -F test_title="CycloneDX Scan Image" \
            -F active="true" \
            -F verified="true" \
            -F file=@trivy_image.json \
            -F product_name=${productName} \
            -F engagement_name=${engagementName} \
            -F deduplication_on_engagement=true \
            -F close_old_findings=true \
            -F auto_create_context=true
        """
  }
}
