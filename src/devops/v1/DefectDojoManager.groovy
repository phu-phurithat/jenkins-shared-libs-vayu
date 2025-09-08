package devops.v1
def ImportReport() {
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
            -F product_name='sample' \
            -F engagement_name='ci-security-scan' \
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
            -F file=@trivy_vuln.json \
            -F product_name='sample' \
            -F engagement_name='ci-security-scan' \
            -F deduplication_on_engagement=true \
            -F close_old_findings=true \
            -F auto_create_context=true
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
            -F product_name='sample' \
            -F engagement_name='ci-security-scan' \
            -F deduplication_on_engagement=true \
            -F close_old_findings=true \
            -F auto_create_context=true
        """
}
}