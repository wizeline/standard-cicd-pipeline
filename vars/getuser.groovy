def call(){
    wrap([$class: 'BuildUser']) {
      return BUILD_USER
    }
}
