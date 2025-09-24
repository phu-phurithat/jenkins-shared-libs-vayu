@Library('shared-libs@feature/deploy') _

microservicePipeline(
  DEPLOYMENT_REPO: "<GIT_REPO_URL_HTTPS>",
  TRIGGER_TOKEN: "GITHUB_TOKEN",
  MICROSERVICE_NAME: "<MICROSERVICE_NAME>",  // Not Application Name, just Microservice Name (e.g., catalog, user
  BRANCH: "<BRANCH_NAME>",
  AUTO_DEPLOY: true,
  TARGET_ENV: "<TARGET_ENV>"  // e.g., dev, sit, uat, prod
  IMAGE_TAG: "<IMAGE_TAG>" // e.g. v1.0.0 (optional, default: git commit hash)
)