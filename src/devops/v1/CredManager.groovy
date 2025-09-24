package devops.v1



  def globalENV(){
    env.SONAR_BASE_URL = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
    env.TRIVY_BASE_URL = "http://trivy.trivy.svc.cluster.local:4954"
    env.DEFECTDOJO_BASE_URL = "https://defectdojo.phurithat.site"
    env.BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
    env.DOCKER_CONFIG     = '/root/.docker'
    env.DOJO_KEY = "defectdojo_api_key"
    env.HARBOR_CRED = "harbor-cred"
    env.JENKINS_CRED = "jenkins-cred"
    env.GITLAB_NONPROD_KEY = "gitlab-nonprod-key"
    env.GITLAB_PROD_KEY = "gitlab-prod-key"
    env.HELM_PATH = "vayu-helm"
    env.HELM_NONPROD_REPO = "https://gitlab.devopsnonprd.vayuktbcs/api/v4/projects/7410/packages/helm/stable"
    env.OCP_NONPROD_AGENT = "ocp-nonprod-agent"
    env.OCP_PROD_AGENT = "ocp-prod-agent"
    env.SONAR_TOKEN =  'sonar_token'
    
  }
