pipeline {
    agent {
        docker {
            image 'maven:3.8.2-openjdk-17'
        }
    }
    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=/root/.m2/repository'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                sh './mvnw clean package spring-boot:repackage'
            }
        }
        stage('Archive') {
            steps {
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
        }
    }
    post {
        always {
            echo 'Cleaning up workspace...'
            deleteDir()
        }
        success {
            echo 'Build succeeded!'
        }
        failure {
            echo 'Build failed!'
        }
    }
}
