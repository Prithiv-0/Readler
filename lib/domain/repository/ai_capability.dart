class AiCapability {
  final bool enabled;
  final bool hasApiKey;
  final bool hasNetwork;

  const AiCapability({
    this.enabled = false,
    this.hasApiKey = false,
    this.hasNetwork = false,
  });

  bool get canRun => enabled && hasApiKey && hasNetwork;
}
