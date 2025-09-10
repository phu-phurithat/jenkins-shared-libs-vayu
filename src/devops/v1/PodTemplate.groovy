package devops.v1

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions

class PodTemplate implements Serializable {

  def base = [
    apiVersion: 'v1',
    kind: 'Pod',
    metadata: [
      labels: [ 'jenkins-pod': 'true' ]
    ],
    spec: [
      containers: [],
      volumes: [
      [
        name: 'shared',
        emptyDir: [:]
      ]]
  ]
  ]

  PodTemplate injectConfig(config, args) {
    // Build tool container
    String tool = (config.build_tool ?: '').toString().toLowerCase()
    switch (tool) {
      case 'maven':  addMaven();  break
      case 'nodejs': addNode();   break
      case 'go':     addGo();     break
      case 'python': addPython(); break
      default:
        // no-op; still usable as a generic agent pod
        break
    }

    // If we build/push images, add buildkit + secret volume
    if (config.build_image in [true, 'true']) {
      addBuildkit()
      addHarborSecretVolume()
    }

    // Security section
    Map sec = (config.security instanceof Map) ? (config.security as Map) : [:]
    if (sec.image in [true, 'true'] || sec.dependency in [true, 'true']) {
      addTrivy()
    }
    if (sec.code in [true, 'true']) {
      addSonarScanner()
    }
    if (sec.secret in [true, 'true']) {
      addGitLeaks()
    }

    if (args.AUTO_DEPLOY in [true, 'true']) {
      addKubectl()
      addHelm()
    }
    return this
  }

  PodTemplate addMaven(String javaVersion) {
    javaVersion = javaVersion.tokenize('.').first() // eg. 21.2.0 -> 21
    if (javaVersion != '8' || javaVersion != '11' || javaVersion != '17' || javaVersion != '21') {
      return { error "Unsupported Java version for Maven: ${javaVersion}. Supported: 8, 11, 17, 21." }
    }
    addContainerIfMissing([
      name           : 'maven',
      image          : "maven:3.9.8-eclipse-temurin-${javaVersion}",
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true,
    // resources      : [
    //   requests: ['ephemeral-storage': '512Mi'],
    //   limits  : ['ephemeral-storage': '1Gi']
    // ]
    ])
    return this
  }

  PodTemplate addNode() {
    addContainerIfMissing([
      name           : 'nodejs',
      image          : 'node:20',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true
    ])
    return this
  }

  PodTemplate addGo() {
    addContainerIfMissing([
      name           : 'golang',
      image          : 'golang:1.23',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true
    ])
    return this
  }

  PodTemplate addPython() {
    addContainerIfMissing([
      name           : 'python',
      image          : 'python:3.12',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true
    ])
    return this
  }

  PodTemplate addTrivy() {
    addContainerIfMissing([
      name           : 'trivy',
      image          : 'aquasec/trivy:0.54.1',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true,
      volumeMounts   : [
        [ name: 'shared',        mountPath: '/jenkins-agent' ],
        [ name: 'harbor-secret', mountPath: '/root/.docker' ]
      ],
      resources      : [
        requests: [  'ephemeral-storage': '2Gi' ],
        limits  : [  'ephemeral-storage': '4Gi' ]
      ]
    ])
    addHarborSecretVolume()
    return this
  }

  PodTemplate addSonarScanner() {
    addContainerIfMissing([
      name           : 'sonarqube',
      image          : 'sonarsource/sonar-scanner-cli:11.4',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true,
    // resources      : [
    //   requests: [ cpu: '500m', memory: '2Gi', 'ephemeral-storage': '512Mi' ],
    //   limits  : [ cpu: '1000m', memory: '4Gi', 'ephemeral-storage': '1Gi' ]
    // ]
    ])
    return this
  }

  PodTemplate addKubectl() {
    addContainerIfMissing([
      name           : 'kubectl',
      image          : 'alpine/k8s:1.31.12',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true
    ])
    return this
  }

  PodTemplate addHelm() {
    addContainerIfMissing([
      name           : 'helm',
      image          : 'alpine/helm:3.15.4',
      imagePullPolicy: 'Always',
      command        : ['cat'],
      tty            : true
    ])
    return this
  }

  PodTemplate addBuildkit() {
    addContainerIfMissing([
      name           : 'buildkit',
      image          : 'moby/buildkit:latest',
      command        : ['cat'],
      tty            : true,
      securityContext: [ privileged: true ],
      volumeMounts   : [
        [ name: 'shared',        mountPath: '/jenkins-agent' ],
        [ name: 'harbor-secret', mountPath: '/root/.docker' ]
      ]
    ])
    addHarborSecretVolume()
    return this
  }

  PodTemplate addHarborSecretVolume() {
    addVolumeIfMissing([
      name  : 'harbor-secret',
      secret: [
        secretName: 'harbor-registry',
        items     : [[ key: '.dockerconfigjson', path: 'config.json' ]]
      ]
    ])
    return this
  }

  PodTemplate addGitLeaks() {
    addContainerIfMissing([
      name           : 'gitleaks',
      image          : 'zricethezav/gitleaks:v8.28.0',   // pick a stable tag
      imagePullPolicy: 'Always',
      command        : ['cat'],  // keep alive until Jenkins runs inside container
      tty            : true,
      // resources      : [
      //   requests: [ cpu: '200m', memory: '512Mi', 'ephemeral-storage': '512Mi' ],
      //   limits  : [ cpu: '500m', memory: '1Gi',  'ephemeral-storage': '1Gi' ]
      // ],
      volumeMounts   : [
        [ name: 'shared', mountPath: '/jenkins-agent' ] // optional shared workspace
      ]
    ])
    return this
  }

  String toString() {
    def options = new DumperOptions()
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    def yaml = new Yaml(options)
    return yaml.dump(base)
  }

  // ===== Internals / helpers =====
  private void addContainerIfMissing(Map c) {
    List<Map> containers = base.spec.containers as List<Map>
    if (!containers.any { it.name == c.name }) {
      containers << c
  }
}
  private void addVolumeIfMissing(Map v) {
    List<Map> vols = base.spec.volumes as List<Map>
    if (!vols.any { it.name == v.name }) {
      vols << v
  }
  }

}
