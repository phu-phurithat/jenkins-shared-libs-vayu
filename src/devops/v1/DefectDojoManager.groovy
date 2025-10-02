package devops.v1

def importReport(productName, engagementName) {
  // def productName = "${fullPath}-${component}"
  // def engagementName = "${component}:${imageTag}"
  def productJson
  def productId
  def engagementJson
  def engagementId
  def today = sh(script: 'date +%Y-%m-%d', returnStdout: true).trim()
  def nextWeek = sh(script: 'date -d +7days +%Y-%m-%d', returnStdout: true).trim()
  withCredentials([string(credentialsId: DOJO_KEY, variable: 'DOJO_KEY')]) {
    // Check if Product exists
    def productCheck = sh(
                        script: """
                          curl -s -k \
                            "${DEFECTDOJO_BASE_URL}/api/v2/products/?name=${productName}" \
                            -H "Authorization: Token $DOJO_KEY"
                        """,
                        returnStdout: true
                        )

    echo "productCheck: ${productCheck}"
    productJson = readJSON text: productCheck
    if (productJson != null && productJson.count.toInteger() > 0) {
      productId = productJson.results[0].id
      echo "✅ Found Product ID: ${productId}"
                    } else {
      echo "❌ Product ${productName} not found!"
      echo "Creating new product...productName: ${productName}"
      productJson = sh(
        script: """
          curl -s -k -X POST "https://defectdojo.phurithat.site/api/v2/products/" \
            -H "Authorization: Token $DOJO_KEY" \
            -H "Content-Type: application/json" \
            -d '{
                  "name": "${productName}",
                  "description": "Created from Jenkins",
                  "prod_type": 1
                }'
        """,
        returnStdout: true
    )

      def productObj = readJSON text: productJson
      productId = productObj.id
      // echo "✅ New :prooduct object:${productObj}"
      echo "✅ New Product ID: ${productId}"

      def engagementCheck = sh(
                        script: """
                          curl -s -k "${DEFECTDOJO_BASE_URL}/api/v2/engagements/?name=${productName}&product=${productId}" \
                            -H "Authorization: Token $DOJO_KEY"
                        """,
                        returnStdout: true
                    )
      engagementJson = readJSON text: engagementCheck
      if (engagementJson != null && engagementJson.count.toInteger() > 0) {
        engagementId = engagementJson.results[0].id
        echo "✅ Found Engagement ID: ${engagementId}"
                    } else {
        echo "❌ Engagement ${engagementName} not found in Product ID ${productId}!"
        echo "Creating new engagement...engagementName: ${engagementName}"
        engagementJson = sh(
  script: """
    curl -s -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/engagements/" \
      -H "Authorization: Token $DOJO_KEY" \
      -H "Content-Type: application/json" \
      -d '{
            "name": "${engagementName}",
            "product": "${productId}",
            "status": "In Progress",
            "target_start": "${today}",
            "target_end": "${nextWeek}"
          }'
  """,
  returnStdout: true
)
      }

      def engagementObj = readJSON text: engagementJson
      engagementId = engagementObj.id
      // echo "✅ New :engagementObj:${engagementObj}"
      echo "✅ New Engagement ID: ${engagementId}"
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
          curl -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/reimport-scan/" \
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
          curl -k -X POST "${DEFECTDOJO_BASE_URL}/api/v2/reimport-scan/" \
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
