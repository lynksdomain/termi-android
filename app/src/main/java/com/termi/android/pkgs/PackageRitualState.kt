package com.termi.android.pkgs

// The Termi Package "ritual" states — shared by whatever transport is
// currently active. Android went through a native-vs-bridge phase (Nearby
// Connections vs. mDNS+HTTP) before settling on the bridge as the ONE
// universal Android transport (Nearby Connections proved unreliable on
// real target hardware — custom-ROM retro handhelds frequently lack a
// certified Google Play Services build). iOS keeps a real split
// (MultipeerConnectivity for iOS-to-iOS, this same bridge for reaching
// Android), since Apple's tightly controlled hardware doesn't have
// Android's fragmentation problem — so this state shape is kept
// transport-agnostic rather than folded directly into PackageBridgeManager,
// in case a second transport is ever reintroduced.
sealed class PackageRitualState {
    object Idle : PackageRitualState()
    object Dialing : PackageRitualState()
    object Listening : PackageRitualState()
    data class CarrierDetected(val peerName: String) : PackageRitualState()
    data class AwaitingAcceptance(val peerName: String) : PackageRitualState()
    data class IncomingInvite(val peerName: String) : PackageRitualState()
    data class LinkEstablished(val peerName: String) : PackageRitualState()
    data class Transferring(val progress: Float) : PackageRitualState()
    object CompletedSend : PackageRitualState()
    data class CompletedReceive(val message: String) : PackageRitualState()
    data class Failed(val reason: String) : PackageRitualState()
    object Cancelled : PackageRitualState()
}
