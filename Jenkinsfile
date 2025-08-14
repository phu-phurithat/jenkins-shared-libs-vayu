pipeline {
    agent any
    //   environment {
    //   JAVA_HOME = '/opt/java/openjdk'   // <--- ปรับให้ตรง path ในเครื่อง agent
    // PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
    //   }
    tools {
        maven 'Maven'
    }
    stages {
        stage('Check out') {
            steps {
                git branch: 'main', url:'https://github.com/shazforiot/java_maven_project.git'
            }
        }
        stage('Clean') {
            steps {
                sh 'mvn clean'
            }
        }
        stage('Build') {
            steps {
                //echo "JAVA_HOME is: ${env.JAVA_HOME}"
                // sh 'echo $JAVA_HOME'
                withMaven(globalMavenSettingsConfig: '', jdk: 'JDK-24', maven: 'Maven', mavenSettingsConfig: '', traceability: true) {
                    sh 'mvn install'
                }
            }
        }
        stage('Scanner') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: 'sonar_token', variable: 'SONAR_TOKEN')]) {
                        sh 'mvn sonar:sonar -Dsonar.login=$SONAR_TOKEN Dsonar.projectKey=Java'
                    }
                }
            }
        }
    }
}
