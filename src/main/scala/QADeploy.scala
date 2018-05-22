object QADeploy extends Deployer {
  override val serviceName = args.head
  println(serviceName)

  system.terminate()
}
