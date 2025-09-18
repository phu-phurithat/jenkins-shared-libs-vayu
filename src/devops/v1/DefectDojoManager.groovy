package devops.v1
String productName
String engagementName
def ImportReport(fullPath, imageTag,component) {
  productName= "${fullPath}-${component}"
  engagementName= "${component}:${imageTag}"
  
  def productId = sh(
                        script: """
                            curl -s -k -H "Authorization: Token ${DOJO_KEY}" \
                                 "${DEFECTDOJO_BASE_URL}/api/v2/products/?name=${productName}" \
                                 | jq '.results[0].id'
                        """,
                        returnStdout: true
                        ).trim()
   if (productId == "null" || productId == "") {
                        echo "Product not found. Creating..."
                        productId = sh(
                            script: """
                                curl -s -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/products/" \
                                    -H "Authorization: Token ${DOJO_KEY}" \
                                    -H "Content-Type: application/json" \
                                    -d '{"name": "${productName}", "description": "Auto-created from Jenkins","prod_type": 1}' \
                                    | jq '.id'
                            """,
                            returnStdout: true
                        ).trim()
                    }

  def engagementId = sh(
                        script: """
                            curl -s -k -H "Authorization: Token ${DOJO_KEY}" \
                                 "${DEFECTDOJO_BASE_URL}/api/v2/engagements/?name=${engagementName}&product=${productId}" \
                                 | jq '.results[0].id'
                        """,
                        returnStdout: true
                       ).trim()
   if (engagementId == "null" || engagementId == "") {
                        echo "Engagement not found. Creating..."
                        engagementId = sh(
                            script: """
                                curl -s -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/engagements/" \
                                    -H "Authorization: Token ${DOJO_KEY}" \
                                    -H "Content-Type: application/json" \
                                    -d '{
                                        "name": "${engagementName}",
                                        "product": ${productId},
                                        "status": "In Progress",
                                        "engagement_type": "CI/CD"
                                    }' | jq '.id'
                            """,
                            returnStdout: true
                        ).trim()
                    }
      withCredentials([string(credentialsId: DOJO_KEY, variable: 'DOJO_KEY')]) {
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