package com.inscopexlabs.gitana.gitbridge.approval

/**
 * Routes notification actions to the shared approval coordinator.
 *
 * This is the action handler used by the future Android BroadcastReceiver/PendingIntent
 * integration. It deliberately has no Android dependency so the approval decision
 * logic remains independently testable.
 */
class ApprovalNotificationReceiver(
    private val coordinator: ApprovalCoordinator,
) {
    /** Handles the user's Approve action for [requestId]. */
    fun onApprove(requestId: String): Boolean = coordinator.approve(requestId)

    /** Handles the user's Decline action for [requestId]. */
    fun onDeny(requestId: String): Boolean = coordinator.deny(requestId)
}
